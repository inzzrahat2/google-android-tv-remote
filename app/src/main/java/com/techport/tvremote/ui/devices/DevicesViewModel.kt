package com.techport.tvremote.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techport.tvremote.core.discovery.DiscoveryService
import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.DiscoveredDevice
import com.techport.tvremote.domain.model.PairingState
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.RemoteException
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.remote.RemoteController
import com.techport.tvremote.domain.repository.DeviceRepository
import com.techport.tvremote.protocol.core.PairingSession
import com.techport.tvremote.protocol.core.ProtocolRegistry
import com.techport.tvremote.protocol.mock.MockTvProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NearbyUi(val searching: Boolean = true, val devices: List<DiscoveredDevice> = emptyList(), val onlineIds: Set<String> = emptySet())

data class PairingUi(val target: DiscoveredDevice, val state: PairingState, val simulatedCode: String?)

class DevicesViewModel(
    private val devices: DeviceRepository,
    private val controller: RemoteController,
    discovery: DiscoveryService,
    private val protocols: ProtocolRegistry,
    private val forgetDevice: suspend (TvDevice) -> Unit,
    private val logger: AppLogger,
    /** Builds a pairing target from a typed IP/host name; throws IllegalArgumentException if invalid. */
    private val manualTarget: ((String) -> DiscoveredDevice)? = null,
) : ViewModel() {

    val saved: StateFlow<List<TvDevice>> =
        devices.devices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connection: StateFlow<ConnectionState> = controller.state

    val needsLocalNetwork: Boolean = protocols.protocols.any { it.usesLocalNetwork }

    /** Discovery runs only while this screen is visible (WhileSubscribed). */
    /** Bumped to rescan, e.g. once local network access is granted (a blocked scan never recovers). */
    private val scanKey = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val nearby: StateFlow<NearbyUi> = scanKey.flatMapLatest {
        combine(
            discovery.discover(),
            devices.devices,
            searchWindow(),
        ) { found, savedList, searching ->
            val savedIds = savedList.mapTo(HashSet()) { it.id }
            NearbyUi(
                searching = searching,
                devices = found.filterNot { it.id in savedIds },
                onlineIds = found.mapTo(HashSet()) { it.id },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NearbyUi())

    fun restartDiscovery() {
        scanKey.value++
    }

    private val _pairing = MutableStateFlow<PairingUi?>(null)
    val pairing: StateFlow<PairingUi?> = _pairing.asStateFlow()
    private var pairingSession: PairingSession? = null
    private var pairingJob: Job? = null

    private val _events = Channel<DevicesEvent>(Channel.BUFFERED)
    val events: Flow<DevicesEvent> = _events.receiveAsFlow()

    fun connect(device: TvDevice) {
        controller.connect(device)
        _events.trySend(DevicesEvent.OpenRemote)
    }

    fun disconnect() = controller.disconnect()

    fun rename(device: TvDevice, name: String) = viewModelScope.launch { devices.rename(device.id, name) }

    fun toggleFavorite(device: TvDevice) = viewModelScope.launch { devices.setFavorite(device.id, !device.isFavorite) }

    fun move(device: TvDevice, delta: Int) = viewModelScope.launch { devices.move(device.id, delta) }

    fun forget(device: TvDevice) = viewModelScope.launch { forgetDevice(device) }

    fun testConnection() = viewModelScope.launch {
        val latency = runCatching { controller.measureLatency() }.getOrNull()
        _events.send(DevicesEvent.Latency(latency))
    }

    fun pair(target: DiscoveredDevice) {
        cancelPairing()
        val protocol = protocols[target.protocolId] ?: return
        val session = protocol.createPairing(target)
        pairingSession = session
        val simulatedCode = (protocol as? MockTvProtocol)?.pairingCode
        pairingJob = viewModelScope.launch {
            launch {
                session.state.collect { state ->
                    _pairing.value = PairingUi(target, state, simulatedCode)
                    if (state is PairingState.Paired) onPaired(state.device)
                }
            }
            guard(session) { session.start() }
        }
    }

    val supportsManualEntry: Boolean get() = manualTarget != null

    /** Returns false if [host] is not a valid address. */
    fun pairManually(host: String): Boolean {
        val target = runCatching { manualTarget?.invoke(host.trim()) }.getOrNull() ?: return false
        pair(target)
        return true
    }

    fun pairAgain(device: TvDevice) = pair(
        DiscoveredDevice(device.id, device.name, device.host, device.port, device.protocolId, device.model),
    )

    fun submitCode(code: String) {
        val session = pairingSession ?: return
        viewModelScope.launch { guard(session) { session.submitCode(code) } }
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        pairingSession?.cancel()
        pairingSession = null
        _pairing.value = null
    }

    private suspend fun guard(session: PairingSession, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = (e as? RemoteException)?.error ?: RemoteError.UNKNOWN
            logger.w(TAG, "Pairing failed: $error", e)
            _pairing.value = _pairing.value?.copy(state = PairingState.Failed(error))
            session.cancel()
        }
    }

    private suspend fun onPaired(device: TvDevice) {
        val existing = devices.get(device.id)
        devices.upsert(
            existing?.copy(host = device.host, port = device.port, model = device.model ?: existing.model) ?: device,
        )
        val stored = devices.get(device.id) ?: device
        logger.i(TAG, "Paired with ${stored.name}")
        _pairing.value = null
        pairingSession = null
        connect(stored)
    }

    override fun onCleared() {
        pairingSession?.cancel()
    }

    private fun searchWindow(): Flow<Boolean> = flow {
        emit(true)
        delay(SEARCH_WINDOW_MS)
        emit(false)
    }

    private companion object {
        const val TAG = "Devices"
        const val SEARCH_WINDOW_MS = 8_000L
    }
}

sealed interface DevicesEvent {
    data object OpenRemote : DevicesEvent
    data class Latency(val millis: Long?) : DevicesEvent
}
