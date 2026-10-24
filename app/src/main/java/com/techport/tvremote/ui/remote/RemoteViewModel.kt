package com.techport.tvremote.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techport.tvremote.domain.model.AppSettings
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.domain.remote.RemoteController
import com.techport.tvremote.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RemoteViewModel(
    private val controller: RemoteController,
    devices: DeviceRepository,
    val settings: StateFlow<AppSettings>,
) : ViewModel() {

    val state: StateFlow<ConnectionState> = controller.state
    val volume: StateFlow<VolumeState?> = controller.volume

    val savedDevices: StateFlow<List<TvDevice>> =
        devices.devices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onKey(key: RemoteKey, action: KeyAction) = controller.send(RemoteCommand.Key(key, action))

    fun switchTo(device: TvDevice) = controller.connect(device)

    fun retry() = controller.retry()
}
