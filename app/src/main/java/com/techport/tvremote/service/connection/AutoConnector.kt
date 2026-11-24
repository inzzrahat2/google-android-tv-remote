package com.techport.tvremote.service.connection

import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.device
import com.techport.tvremote.domain.remote.RemoteController
import com.techport.tvremote.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.first

/**
 * App start: connect to the last used TV immediately, without going through discovery.
 * Falls back to the only saved TV when there is exactly one.
 */
class AutoConnector(
    private val devices: DeviceRepository,
    private val controller: RemoteController,
) {
    suspend fun connectPreferred() {
        if (controller.state.value != ConnectionState.Idle) return
        val target = preferredDevice(ConnectionState.Idle, devices.devices.first(), devices.lastSelectedId.first()) ?: return
        controller.connect(target)
    }
}

/**
 * The TV the app, a widget or the tile acts on: the one the app is on now, else the last
 * selected one, else the only saved one.
 */
fun preferredDevice(state: ConnectionState, saved: List<TvDevice>, lastSelectedId: String?): TvDevice? =
    state.device ?: saved.find { it.id == lastSelectedId } ?: saved.singleOrNull()
