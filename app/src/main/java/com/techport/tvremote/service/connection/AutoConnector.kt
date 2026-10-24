package com.techport.tvremote.service.connection

import com.techport.tvremote.domain.model.ConnectionState
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
        val saved = devices.devices.first()
        val lastId = devices.lastSelectedId.first()
        val target = saved.find { it.id == lastId } ?: saved.singleOrNull() ?: return
        controller.connect(target)
    }
}
