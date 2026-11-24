package com.techport.tvremote.protocol.androidtv

import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.RemoteException
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.protocol.androidtv.wire.AppLinkLaunchRequest
import com.techport.tvremote.protocol.androidtv.wire.DeviceInfo
import com.techport.tvremote.protocol.androidtv.wire.EditInfo
import com.techport.tvremote.protocol.androidtv.wire.Feature
import com.techport.tvremote.protocol.androidtv.wire.ImeBatchEdit
import com.techport.tvremote.protocol.androidtv.wire.ImeObject
import com.techport.tvremote.protocol.androidtv.wire.KeyDirection
import com.techport.tvremote.protocol.androidtv.wire.KeyInject
import com.techport.tvremote.protocol.androidtv.wire.PingResponse
import com.techport.tvremote.protocol.androidtv.wire.RemoteConfigure
import com.techport.tvremote.protocol.androidtv.wire.RemoteMessage
import com.techport.tvremote.protocol.androidtv.wire.RemoteSetActive
import com.techport.tvremote.protocol.androidtv.wire.TextFieldStatus
import com.techport.tvremote.protocol.androidtv.wire.VoiceBegin
import com.techport.tvremote.protocol.androidtv.wire.VoiceEnd
import com.techport.tvremote.protocol.androidtv.wire.VoicePayload
import com.techport.tvremote.protocol.core.RemoteSession
import com.techport.tvremote.protocol.core.VoiceSession
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A remote session on port 6466 (Android TV Remote Service v2).
 *
 * Handshake: the TV sends configure (we answer with the feature intersection and our device info),
 * set_active (we answer with the active features) and finally start, after which the session is
 * ready. One reader coroutine handles everything the TV sends, answering every ping; writes are
 * serialized. If nothing arrives for [idleTimeoutMs] (the TV pings every ~5 s) the session ends.
 */
class AndroidTvSession internal constructor(
    private val transport: MessageTransport,
    private val logger: AppLogger,
    /** Address this session actually uses; differs from the saved one after an NSD re-resolve. */
    override val connectedHost: String,
    override val connectedPort: Int,
    private val requestedFeatures: Int = DEFAULT_FEATURES,
    private val deviceInfo: DeviceInfo = DeviceInfo(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idleTimeoutMs: Long = IDLE_TIMEOUT_MS,
    private val voiceBeginTimeoutMs: Long = VOICE_BEGIN_TIMEOUT_MS,
) : RemoteSession {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val ready = CompletableDeferred<Unit>()
    private val closed = CompletableDeferred<RemoteError?>()
    private val started = AtomicBoolean(false)
    private val writeLock = Mutex()
    private val voiceLock = Mutex()
    private val activity = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var activeFeatures = requestedFeatures
    @Volatile private var configured = false
    @Volatile private var volumeReported = false
    @Volatile private var voiceUnavailable = false
    @Volatile private var imeCounter = 0
    @Volatile private var fieldCounter = 0
    @Volatile private var pendingVoice: CompletableDeferred<Int>? = null
    @Volatile private var voiceSessionId: Int? = null

    private val _capabilities = MutableStateFlow(RemoteCapabilities.NONE)
    override val capabilities: StateFlow<RemoteCapabilities> = _capabilities.asStateFlow()

    private val _volume = MutableStateFlow<VolumeState?>(null)
    override val volume: StateFlow<VolumeState?> = _volume.asStateFlow()

    private val _currentApp = MutableStateFlow<String?>(null)
    override val currentApp: StateFlow<String?> = _currentApp.asStateFlow()

    private val _textField = MutableStateFlow<TvTextField?>(null)
    override val textField: StateFlow<TvTextField?> = _textField.asStateFlow()

    private val _screenOn = MutableStateFlow<Boolean?>(null)

    /** RemoteStart.started: whether the TV says its screen is on. Not a confirmed power state. */
    val screenOn: StateFlow<Boolean?> = _screenOn.asStateFlow()

    /** Starts the reader and the idle watchdog. Call once, then [awaitReady]. */
    internal fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { readLoop() }
        scope.launch { watchdog() }
    }

    /** Suspends until the TV sent remote_start; throws [RemoteException] if the session ended first. */
    internal suspend fun awaitReady() = ready.await()

    override suspend fun execute(command: RemoteCommand) {
        if (!ready.isCompleted || closed.isCompleted) throw RemoteException(RemoteError.CONNECTION_LOST, "Session not open")
        when (command) {
            is RemoteCommand.Key -> {
                requireFeature(Feature.KEY)
                send(RemoteMessage(keyInject = KeyInject(command.key.keyCode, command.action.toDirection())))
            }
            is RemoteCommand.Text -> {
                requireFeature(Feature.IME)
                if (command.text.isNotEmpty()) send(textMessage(command.text, imeCounter, fieldCounter))
            }
            is RemoteCommand.LaunchApp -> {
                requireFeature(Feature.APP_LINK)
                send(RemoteMessage(appLinkLaunch = AppLinkLaunchRequest(command.link)))
            }
        }
    }

    override suspend fun startVoice(): VoiceSession? {
        if (!_capabilities.value.voice || !ready.isCompleted || closed.isCompleted) return null
        return voiceLock.withLock {
            // A session left open (caller cancelled before ending it) would block voice until
            // reconnect; close it on the TV and start fresh instead.
            voiceSessionId?.let { stale ->
                logger.w(TAG, "Closing stale voice session $stale")
                runCatching { send(RemoteMessage(voiceEnd = VoiceEnd(stale))) }
                voiceSessionId = null
            }
            val begin = CompletableDeferred<Int>()
            pendingVoice = begin
            try {
                send(RemoteMessage(keyInject = KeyInject(RemoteKey.SEARCH.keyCode, KeyDirection.SHORT)))
                val id = withTimeoutOrNull(voiceBeginTimeoutMs) { begin.await() }
                if (id == null) {
                    // The VOICE bit was negotiated but the TV did not open a voice session: treat voice as
                    // unavailable for this session rather than pretend it works.
                    logger.w(TAG, "TV did not start a voice session; hiding voice for this session")
                    voiceUnavailable = true
                    publishCapabilities()
                    return@withLock null
                }
                try {
                    send(RemoteMessage(voiceBegin = VoiceBegin(id)))
                } catch (e: CancellationException) {
                    // The TV is listening on [id]; don't leave it open with nobody to end it.
                    withContext(NonCancellable) { runCatching { send(RemoteMessage(voiceEnd = VoiceEnd(id))) } }
                    throw e
                }
                voiceSessionId = id
                logger.i(TAG, "Voice session $id open")
                AndroidTvVoiceSession(id)
            } finally {
                pendingVoice = null
            }
        }
    }

    /** The TV only pings us; there is no client-initiated round trip to time, so this is unknown. */
    override suspend fun measureLatency(): Long? = null

    override suspend fun awaitClosed(): RemoteError? = closed.await()

    override fun close() = finish(null)

    private suspend fun readLoop() {
        try {
            while (true) {
                val bytes = transport.receive()
                activity.trySend(Unit)
                handle(RemoteMessage.decode(bytes))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!closed.isCompleted) {
                val error = mapIoError(e, established = ready.isCompleted)
                logger.w(TAG, "Session read failed: $error", e)
                finish(error)
            }
        }
    }

    private suspend fun watchdog() {
        while (true) {
            if (withTimeoutOrNull(idleTimeoutMs) { activity.receive() } == null) {
                logger.w(TAG, "Nothing from the TV for ${idleTimeoutMs}ms; closing")
                finish(if (ready.isCompleted) RemoteError.CONNECTION_LOST else RemoteError.TIMEOUT)
                return
            }
        }
    }

    private suspend fun handle(msg: RemoteMessage) {
        msg.pingRequest?.let {
            send(RemoteMessage(pingResponse = PingResponse(it.val1)))
            return
        }
        msg.configure?.let { cfg ->
            activeFeatures = requestedFeatures and cfg.code1
            configured = true
            cfg.deviceInfo?.let { d -> logger.i(TAG, "TV: ${d.vendor} ${d.model}, remote service ${d.appVersion}") }
            logger.i(TAG, "Features: TV 0x${cfg.code1.toString(16)}, active 0x${activeFeatures.toString(16)}")
            send(RemoteMessage(configure = RemoteConfigure(activeFeatures, deviceInfo)))
            publishCapabilities()
            return
        }
        msg.setActive?.let {
            send(RemoteMessage(setActive = RemoteSetActive(activeFeatures)))
            return
        }
        msg.start?.let {
            _screenOn.value = it.started
            if (!configured) logger.w(TAG, "TV started the session without configure")
            publishCapabilities()
            if (ready.complete(Unit)) logger.i(TAG, "Session ready")
            return
        }
        msg.setVolumeLevel?.let {
            _volume.value = VolumeState(it.volumeLevel, it.volumeMax, it.volumeMuted)
            if (!volumeReported) {
                volumeReported = true
                publishCapabilities()
            }
            return
        }
        msg.imeKeyInject?.let { inject ->
            val pkg = inject.appInfo?.appPackage?.takeIf { it.isNotEmpty() }
            if (pkg != null && pkg != _currentApp.value) {
                _currentApp.value = pkg
                // Focus moved to another app; a field is only kept if this message reports one.
                if (inject.textFieldStatus == null) _textField.value = null
            }
            inject.textFieldStatus?.let(::showField)
            return
        }
        msg.imeBatchEdit?.let { edit ->
            imeCounter = edit.imeCounter
            fieldCounter = edit.fieldCounter
            edit.editInfo.lastOrNull { it.textFieldStatus != null }?.textFieldStatus?.let { obj ->
                _textField.update { TvTextField(obj.value, it?.label) }
            }
            return
        }
        msg.imeShowRequest?.let { show ->
            show.textFieldStatus?.let(::showField)
            return
        }
        msg.voiceBegin?.let {
            val waiting = pendingVoice
            if (waiting != null) waiting.complete(it.sessionId) else logger.d(TAG, "Ignoring unrequested voice_begin")
            return
        }
        msg.error?.let {
            logger.w(TAG, "TV reported an error for a message we sent")
            return
        }
        logger.d(TAG, "Unhandled ${msg.kind()}")
    }

    private fun showField(status: TextFieldStatus) {
        _textField.value = TvTextField(status.value, status.label.ifBlank { null })
    }

    private fun publishCapabilities() {
        _capabilities.value = capabilitiesFor(activeFeatures, volumeReported, voiceUnavailable)
    }

    private fun requireFeature(bit: Int) {
        if (activeFeatures and bit == 0) throw RemoteException(RemoteError.UNSUPPORTED, "Feature 0x${bit.toString(16)} not active")
    }

    private suspend fun send(msg: RemoteMessage) {
        if (closed.isCompleted) throw RemoteException(RemoteError.CONNECTION_LOST, "Session closed")
        try {
            writeLock.withLock { transport.send(msg.encode()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val error = mapIoError(e, established = ready.isCompleted)
            logger.w(TAG, "Sending ${msg.kind()} failed: $error", e)
            finish(error)
            throw RemoteException(error, "Send failed", e)
        }
    }

    private fun finish(error: RemoteError?) {
        if (!closed.complete(error)) return
        val reason = RemoteException(error ?: RemoteError.CONNECTION_LOST, "Session closed")
        ready.completeExceptionally(reason)
        pendingVoice?.completeExceptionally(reason)
        transport.close()
        scope.cancel()
    }

    private inner class AndroidTvVoiceSession(private val id: Int) : VoiceSession {
        private val chunker = VoiceChunker()
        private val ended = AtomicBoolean(false)

        override suspend fun send(pcm: ByteArray) {
            if (ended.get()) throw RemoteException(RemoteError.PROTOCOL_ERROR, "Voice session already ended")
            for (chunk in chunker.add(pcm)) sendChunk(chunk)
        }

        override suspend fun end() {
            if (!ended.compareAndSet(false, true)) return
            try {
                chunker.flush()?.let { sendChunk(it) }
                this@AndroidTvSession.send(RemoteMessage(voiceEnd = VoiceEnd(id)))
            } finally {
                if (voiceSessionId == id) voiceSessionId = null
            }
        }

        private suspend fun sendChunk(chunk: ByteArray) =
            this@AndroidTvSession.send(RemoteMessage(voicePayload = VoicePayload(id, chunk)))
    }

    internal companion object {
        const val TAG = "AtvSession"
        const val IDLE_TIMEOUT_MS = 16_000L
        const val VOICE_BEGIN_TIMEOUT_MS = 2_000L

        const val DEFAULT_FEATURES = Feature.PING or Feature.KEY or Feature.IME or Feature.VOICE or
            Feature.POWER or Feature.VOLUME or Feature.APP_LINK

        fun KeyAction.toDirection(): Int = when (this) {
            KeyAction.CLICK -> KeyDirection.SHORT
            KeyAction.DOWN -> KeyDirection.START_LONG
            KeyAction.UP -> KeyDirection.END_LONG
        }

        /** Replaces the focused field's text, exactly as the reference client's send_text does. */
        fun textMessage(text: String, imeCounter: Int, fieldCounter: Int): RemoteMessage {
            val last = text.codePointCount(0, text.length) - 1
            return RemoteMessage(
                imeBatchEdit = ImeBatchEdit(
                    imeCounter = imeCounter,
                    fieldCounter = fieldCounter,
                    editInfo = listOf(EditInfo(insert = 1, textFieldStatus = ImeObject(start = last, end = last, value = text))),
                ),
            )
        }

        /** Capability mapping from PROTOCOLS.md. */
        fun capabilitiesFor(active: Int, volumeReported: Boolean, voiceUnavailable: Boolean): RemoteCapabilities {
            fun has(bit: Int) = active and bit != 0
            val keys = has(Feature.KEY)
            return RemoteCapabilities(
                dpad = keys,
                navigation = keys,
                systemKeys = keys,
                keyboard = has(Feature.IME),
                touchpad = keys,
                volume = keys,
                volumeLevel = has(Feature.VOLUME) || volumeReported,
                mute = keys,
                power = has(Feature.POWER),
                mediaControls = keys,
                channels = keys,
                appLaunch = has(Feature.APP_LINK),
                voice = has(Feature.VOICE) && keys && !voiceUnavailable,
                // Delivered as keycodes; whether the firmware honours them needs per-device verification.
                inputSwitching = keys,
            )
        }
    }
}

/**
 * Buffers microphone PCM into voice payloads of 8–20 KiB. Some TVs (e.g. Shield) reject smaller
 * payloads, and padding every small capture buffer would splice silence into speech, so only the
 * final remainder is zero-padded.
 */
internal class VoiceChunker(private val minBytes: Int = MIN_CHUNK, private val maxBytes: Int = MAX_CHUNK) {
    private val buffer = ByteArrayOutputStream()

    fun add(pcm: ByteArray): List<ByteArray> {
        buffer.write(pcm)
        if (buffer.size() < minBytes) return emptyList()
        val all = buffer.toByteArray()
        buffer.reset()
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (all.size - offset >= minBytes) {
            val size = minOf(maxBytes, all.size - offset)
            chunks += all.copyOfRange(offset, offset + size)
            offset += size
        }
        buffer.write(all, offset, all.size - offset)
        return chunks
    }

    /** The remainder, zero-padded to the minimum size; null if nothing is buffered. */
    fun flush(): ByteArray? {
        if (buffer.size() == 0) return null
        val rest = buffer.toByteArray()
        buffer.reset()
        return if (rest.size >= minBytes) rest else rest.copyOf(minBytes)
    }

    companion object {
        const val MIN_CHUNK = 8 * 1024
        const val MAX_CHUNK = 20 * 1024
    }
}
