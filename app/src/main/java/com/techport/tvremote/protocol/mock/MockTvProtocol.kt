package com.techport.tvremote.protocol.mock

import com.techport.tvremote.domain.model.CodeAlphabet
import com.techport.tvremote.domain.model.DiscoveredDevice
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.PairingState
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.RemoteException
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.protocol.core.DiscoverySource
import com.techport.tvremote.protocol.core.PairingSession
import com.techport.tvremote.protocol.core.RemoteProtocol
import com.techport.tvremote.protocol.core.RemoteSession
import com.techport.tvremote.protocol.core.VoiceSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * In-process simulated TV for development, demos and tests. It never touches the network
 * and is only registered in debug builds. It is not a stand-in for real device support.
 */
class MockTvProtocol(
    private val latencyMs: Long = 15,
    val pairingCode: String = DEFAULT_CODE,
    private val capabilities: RemoteCapabilities = FULL_CAPABILITIES,
) : RemoteProtocol {

    override val id: String = ID
    override val displayName: String = "Simulated TV"
    override val usesLocalNetwork: Boolean = false

    private val revoked = ConcurrentHashMap.newKeySet<String>()

    /** Number of upcoming [openSession] calls that should fail as unreachable. */
    val failNextConnects = AtomicInteger(0)

    private val _activeSession = MutableStateFlow<MockTvSession?>(null)
    val activeSession: StateFlow<MockTvSession?> = _activeSession.asStateFlow()

    override val discovery: DiscoverySource = object : DiscoverySource {
        override fun discover(): Flow<List<DiscoveredDevice>> = flow {
            emit(emptyList())
            delay(latencyMs * 10)
            emit(listOf(DEMO_DEVICE))
            awaitCancellation()
        }
    }

    override suspend fun isPaired(device: TvDevice): Boolean = device.id !in revoked

    /** Simulates the TV dropping our credentials (e.g. after a factory reset). */
    fun revokePairing(deviceId: String) {
        revoked += deviceId
    }

    override fun createPairing(target: DiscoveredDevice): PairingSession = MockPairingSession(target)

    override suspend fun openSession(device: TvDevice): RemoteSession {
        delay(latencyMs * 2)
        if (failNextConnects.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
            throw RemoteException(RemoteError.UNREACHABLE, "Simulated unreachable TV")
        }
        if (device.id in revoked) throw RemoteException(RemoteError.PAIRING_REJECTED)
        return MockTvSession(latencyMs, capabilities).also { _activeSession.value = it }
    }

    override suspend fun forget(device: TvDevice) {
        revoked -= device.id
    }

    private inner class MockPairingSession(private val target: DiscoveredDevice) : PairingSession {
        private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
        override val state: StateFlow<PairingState> = _state.asStateFlow()

        override suspend fun start() {
            _state.value = PairingState.Starting
            delay(latencyMs * 4)
            _state.value = PairingState.AwaitingCode(pairingCode.length, CodeAlphabet.HEX)
        }

        override suspend fun submitCode(code: String) {
            _state.value = PairingState.Verifying
            delay(latencyMs * 4)
            _state.value = if (code.equals(pairingCode, ignoreCase = true)) {
                revoked -= target.id
                PairingState.Paired(
                    TvDevice(
                        id = target.id,
                        name = target.name,
                        host = target.host,
                        port = target.port,
                        protocolId = ID,
                        model = target.model,
                    ),
                )
            } else {
                PairingState.AwaitingCode(pairingCode.length, CodeAlphabet.HEX, attemptFailed = true)
            }
        }

        override fun cancel() {
            _state.value = PairingState.Idle
        }
    }

    companion object {
        const val ID = "mock"
        const val DEFAULT_CODE = "A1B2C3"

        val FULL_CAPABILITIES = RemoteCapabilities(
            dpad = true,
            navigation = true,
            systemKeys = true,
            keyboard = true,
            touchpad = true,
            volume = true,
            volumeLevel = true,
            mute = true,
            power = true,
            mediaControls = true,
            channels = true,
            appLaunch = true,
            voice = true,
            inputSwitching = true,
        )

        val DEMO_DEVICE = DiscoveredDevice(
            id = "mock-living-room",
            name = "Simulated TV",
            host = "simulated",
            port = 0,
            protocolId = ID,
            model = "Mock TV",
        )
    }
}

class MockTvSession(
    private val latencyMs: Long,
    capabilities: RemoteCapabilities,
) : RemoteSession {
    private val _capabilities = MutableStateFlow(capabilities)
    override val capabilities: StateFlow<RemoteCapabilities> = _capabilities.asStateFlow()

    private val _volume = MutableStateFlow<VolumeState?>(VolumeState(level = 30, max = 100, muted = false))
    override val volume: StateFlow<VolumeState?> = _volume.asStateFlow()

    private val _currentApp = MutableStateFlow<String?>("com.google.android.tvlauncher")
    override val currentApp: StateFlow<String?> = _currentApp.asStateFlow()

    private val _textField = MutableStateFlow<TvTextField?>(null)
    override val textField: StateFlow<TvTextField?> = _textField.asStateFlow()

    private val closed = CompletableDeferred<RemoteError?>()

    private val _voiceBytes = MutableStateFlow(0)

    /** Total voice bytes received across sessions, for tests. */
    val voiceBytes: StateFlow<Int> = _voiceBytes.asStateFlow()

    private val _received = MutableStateFlow<List<RemoteCommand>>(emptyList())

    /** Commands the simulated TV received, newest last. */
    val received: StateFlow<List<RemoteCommand>> = _received.asStateFlow()

    var isOn: Boolean = true
        private set

    override suspend fun execute(command: RemoteCommand) {
        if (closed.isCompleted) throw RemoteException(RemoteError.CONNECTION_LOST)
        delay(latencyMs)
        _received.update { (it + command).takeLast(100) }
        when (command) {
            is RemoteCommand.Key -> if (command.action != KeyAction.UP) apply(command.key)
            is RemoteCommand.LaunchApp -> _currentApp.value = command.link
            is RemoteCommand.Text -> _textField.update { f -> f?.copy(value = f.value + command.text) }
        }
    }

    private fun apply(key: RemoteKey) {
        _volume.update { v ->
            v ?: return@update null
            when (key) {
                RemoteKey.VOLUME_UP -> v.copy(level = (v.level + 2).coerceAtMost(v.max), muted = false)
                RemoteKey.VOLUME_DOWN -> v.copy(level = (v.level - 2).coerceAtLeast(0))
                RemoteKey.VOLUME_MUTE -> v.copy(muted = !v.muted)
                else -> v
            }
        }
        if (key == RemoteKey.POWER) isOn = !isOn
        if (key == RemoteKey.HOME) _currentApp.value = "com.google.android.tvlauncher"
        if (key == RemoteKey.SEARCH) _textField.value = TvTextField("", "Search")
        if (key == RemoteKey.DEL) _textField.update { f -> f?.copy(value = f.value.dropLast(1)) }
        if (key == RemoteKey.BACK) _textField.value = null
    }

    /** Simulates the TV focusing a text field (as if the user opened a search box). */
    fun focusTextField(label: String = "Search") {
        _textField.value = TvTextField("", label)
    }

    override suspend fun startVoice(): VoiceSession? {
        if (!capabilities.value.voice || closed.isCompleted) return null
        delay(latencyMs)
        return object : VoiceSession {
            override suspend fun send(pcm: ByteArray) {
                _voiceBytes.update { it + pcm.size }
            }

            override suspend fun end() = Unit
        }
    }

    override suspend fun measureLatency(): Long {
        delay(latencyMs)
        return latencyMs
    }

    override suspend fun awaitClosed(): RemoteError? = closed.await()

    /** Simulates the TV going away (sleep, Wi-Fi drop). */
    fun drop(reason: RemoteError = RemoteError.CONNECTION_LOST) {
        closed.complete(reason)
    }

    override fun close() {
        closed.complete(null)
    }
}
