package com.techport.tvremote.widget

import android.content.Context
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps placed widgets showing the right TV (name changes, first pairing, switching TVs)
 * while the app process is alive. No service and no polling: it only reacts to state the app
 * already holds, and only re-renders when the displayed model changes.
 */
class RemoteWidgetUpdater(
    context: Context,
    private val state: Flow<ConnectionState>,
    private val devices: DeviceRepository,
    private val scope: CoroutineScope,
) {
    private val context = context.applicationContext

    fun start() {
        combine(state, devices.devices, devices.lastSelectedId) { s, saved, lastId ->
            WidgetModel(widgetTarget(s, saved, lastId)?.name)
        }
            .distinctUntilChanged()
            .onEach { RemoteWidgets.updateAll(context, it) }
            .launchIn(scope)
    }
}
