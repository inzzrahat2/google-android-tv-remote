package com.techport.tvremote.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techport.tvremote.core.connectivity.NetworkStatus
import com.techport.tvremote.domain.model.AppSettings
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.domain.remote.RemoteController
import com.techport.tvremote.domain.repository.DeviceRepository
import com.techport.tvremote.ui.keyboard.keyboardOpenRequests
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RemoteViewModel(
    private val controller: RemoteController,
    devices: DeviceRepository,
    networkStatus: Flow<NetworkStatus>,
    val settings: StateFlow<AppSettings>,
) : ViewModel() {

    val state: StateFlow<ConnectionState> = controller.state
    val volume: StateFlow<VolumeState?> = controller.volume

    val savedDevices: StateFlow<List<TvDevice>> =
        devices.devices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Optimistic default so nothing flashes "Not on Wi-Fi" before the first reading. */
    val network: StateFlow<NetworkStatus> =
        networkStatus.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkStatus(true, null))

    /** Emits when the TV focuses a text field and auto-open is enabled. */
    val keyboardRequests: Flow<TvTextField> = keyboardOpenRequests(controller.textField, settings)

    fun onKey(key: RemoteKey, action: KeyAction) = controller.send(RemoteCommand.Key(key, action))

    fun launch(link: String) = controller.send(RemoteCommand.LaunchApp(link))

    fun switchTo(device: TvDevice) = controller.connect(device)

    fun retry() = controller.retry()
}
