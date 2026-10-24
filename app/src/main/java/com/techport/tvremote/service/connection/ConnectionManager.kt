package com.techport.tvremote.service.connection

import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.RemoteException
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.domain.model.device
import com.techport.tvremote.domain.remote.RemoteController
import com.techport.tvremote.domain.repository.DeviceRepository
import com.techport.tvremote.protocol.core.ProtocolRegistry
import com.techport.tvremote.protocol.core.RemoteSession
import com.techport.tvremote.protocol.core.VoiceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Numbers for the debug screen. Kept on-device only. */
data class ConnectionMetrics(
    val connectTimeMs: Long? = null,
    val lastCommand: String? = null,
    val lastCommandSendMs: Long? = null,
    val commandsSent: Int = 0,
    val lastError: RemoteError? = null,
)

/**
 * Owns the single live session and the connection state machine.
 *
 * [scope] must be serial (e.g. `Dispatchers.Default.limitedParallelism(1)`): all state
 * transitions run there, so connect/disconnect/reconnect cannot interleave. Protocol
 * adapters do their blocking I/O on their own dispatcher.
 */
class ConnectionManager(
    private val scope: CoroutineScope,
    private val registry: ProtocolRegistry,
    private val devices: DeviceRepository,
    private val logger: AppLogger,
    private val autoReconnect: () -> Boolean = { true },
    private val policy: ReconnectPolicy = ReconnectPolicy(),
    private val connectTimeoutMs: () -> Long = { 8_000 },
    private val clock: () -> Long = System::currentTimeMillis,
) : RemoteController {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _volume = MutableStateFlow<VolumeState?>(null)
    override val volume: StateFlow<VolumeState?> = _volume.asStateFlow()

    private val _currentApp = MutableStateFlow<String?>(null)
    override val currentApp: StateFlow<String?> = _currentApp.asStateFlow()

    private val _textField = MutableStateFlow<TvTextField?>(null)
    override val textField: StateFlow<TvTextField?> = _textField.asStateFlow()

    private val _metrics = MutableStateFlow(ConnectionMetrics())
    val metrics: StateFlow<ConnectionMetrics> = _metrics.asStateFlow()

    @Volatile
    private var session: RemoteSession? = null
    private var connectionJob: Job? = null

    // Bounded so a stalled socket cannot queue minutes of stale key presses.
    private val commands = Channel<RemoteCommand>(capacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Cuts a backoff wait short (network came back, user tapped retry). */
    private val wakeUp = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            for (command in commands) dispatch(command)
        }
    }

    override fun connect(device: TvDevice) {
        scope.launch {
            val previous = connectionJob
            connectionJob = scope.launch {
                previous?.cancelAndJoinQuietly()
                runConnection(device)
            }
            devices.setLastSelected(device.id)
        }
    }

    override fun retry() {
        scope.launch {
            when (val current = _state.value) {
                is ConnectionState.Reconnecting -> wakeUp.tryEmit(Unit)
                is ConnectionState.Disconnected -> connect(current.device)
                is ConnectionState.PairingRequired -> connect(current.device)
                else -> Unit
            }
        }
    }

    override fun disconnect() {
        scope.launch {
            connectionJob?.cancelAndJoinQuietly()
            connectionJob = null
            _state.value = _state.value.device?.let { ConnectionState.Disconnected(it, null) } ?: ConnectionState.Idle
            logger.i(TAG, "Disconnected by user")
        }
    }

    /** Called when the device for [device] was forgotten; drops the session if it is ours. */
    fun release(deviceId: String) {
        scope.launch {
            if (_state.value.device?.id == deviceId) {
                connectionJob?.cancelAndJoinQuietly()
                connectionJob = null
                _state.value = ConnectionState.Idle
            }
        }
    }

    /** Network (re)appeared: retry now instead of waiting out the backoff. */
    fun onNetworkAvailable() {
        wakeUp.tryEmit(Unit)
    }

    override fun send(command: RemoteCommand) {
        commands.trySend(command)
    }

    override suspend fun measureLatency(): Long? = session?.measureLatency()

    override suspend fun startVoice(): VoiceSession? = session?.startVoice()

    private suspend fun dispatch(command: RemoteCommand) {
        val current = session
        if (current == null) {
            logger.d(TAG, "Dropped $command: no session")
            return
        }
        val start = clock()
        try {
            current.execute(command)
            val elapsed = clock() - start
            _metrics.update {
                it.copy(lastCommand = command.toString(), lastCommandSendMs = elapsed, commandsSent = it.commandsSent + 1)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The session's own close signal drives reconnection; just record it here.
            logger.w(TAG, "Command $command failed", e)
        }
    }

    private suspend fun runConnection(device: TvDevice) {
        val protocol = registry[device.protocolId]
        if (protocol == null) {
            logger.e(TAG, "No protocol '${device.protocolId}' for ${device.name}")
            _state.value = ConnectionState.Disconnected(device, RemoteError.UNSUPPORTED)
            return
        }

        var attempt = 0
        var lastError: RemoteError? = null
        while (true) {
            _state.value = if (attempt == 0) {
                ConnectionState.Connecting(device)
            } else {
                ConnectionState.Reconnecting(device, attempt, lastError)
            }

            if (!protocol.isPaired(device)) {
                _state.value = ConnectionState.PairingRequired(device, null)
                return
            }

            val startedAt = clock()
            val opened: RemoteSession? = try {
                withTimeout(connectTimeoutMs()) { protocol.openSession(device) }
            } catch (e: TimeoutCancellationException) {
                lastError = RemoteError.TIMEOUT
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: RemoteException) {
                lastError = e.error
                logger.w(TAG, "Connect to ${device.name} failed: ${e.error}", e.cause)
                null
            } catch (e: Exception) {
                lastError = RemoteError.UNKNOWN
                logger.e(TAG, "Unexpected connect failure", e)
                null
            }

            if (opened == null) {
                _metrics.update { it.copy(lastError = lastError) }
                if (lastError == RemoteError.PAIRING_REJECTED || lastError == RemoteError.CERTIFICATE_CHANGED) {
                    _state.value = ConnectionState.PairingRequired(device, lastError)
                    return
                }
                if (!autoReconnect() || attempt >= policy.maxAttempts || lastError == RemoteError.UNSUPPORTED) {
                    _state.value = ConnectionState.Disconnected(device, lastError)
                    return
                }
                attempt++
                val wait = policy.delayFor(attempt)
                _state.value = ConnectionState.Reconnecting(device, attempt, lastError)
                logger.i(TAG, "Retrying ${device.name} in ${wait}ms (attempt $attempt)")
                withTimeoutOrNull(wait) { wakeUp.first() }
                continue
            }

            val connectMs = clock() - startedAt
            _metrics.update { it.copy(connectTimeMs = connectMs) }
            logger.i(TAG, "Connected to ${device.name} in ${connectMs}ms")

            lastError = runSession(device, opened)
            _metrics.update { it.copy(lastError = lastError) }
            logger.w(TAG, "Session with ${device.name} ended: $lastError")
            if (!autoReconnect()) {
                _state.value = ConnectionState.Disconnected(device, lastError)
                return
            }
            // Reconnect straight away once; backoff applies from the next failure.
            attempt = 1
        }
    }

    /** Runs until the session closes. Returns why it closed. */
    private suspend fun runSession(device: TvDevice, opened: RemoteSession): RemoteError {
        session = opened
        try {
            runCatching { devices.markConnected(device.id, device.host, device.port, clock()) }
                .onFailure { logger.w(TAG, "Could not persist connection time", it) }
            return coroutineScope {
                val mirror = launch {
                    launch { opened.capabilities.collect { _state.value = ConnectionState.Connected(device, it) } }
                    launch { opened.volume.collect { _volume.value = it } }
                    launch { opened.currentApp.collect { _currentApp.value = it } }
                    launch { opened.textField.collect { _textField.value = it } }
                }
                val reason = opened.awaitClosed() ?: RemoteError.CONNECTION_LOST
                mirror.cancel()
                reason
            }
        } finally {
            session = null
            opened.close()
            _volume.value = null
            _currentApp.value = null
            _textField.value = null
        }
    }

    private suspend fun Job.cancelAndJoinQuietly() {
        cancel()
        join()
    }

    private companion object {
        const val TAG = "Connection"
    }
}
