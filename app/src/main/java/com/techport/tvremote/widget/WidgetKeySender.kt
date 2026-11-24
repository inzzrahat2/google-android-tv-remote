package com.techport.tvremote.widget

import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.device
import com.techport.tvremote.domain.remote.RemoteController
import com.techport.tvremote.domain.repository.DeviceRepository
import com.techport.tvremote.service.connection.preferredDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** What a widget shows: the TV its buttons control, or null when none is paired/selected. */
data class WidgetModel(val tvName: String?) {
    val hasTv: Boolean get() = tvName != null
}

/** Result of a widget press and the TV it was meant for (for the message shown). */
data class WidgetKeyOutcome(val result: WidgetKeyResult, val device: TvDevice?)

enum class WidgetKeyResult {
    SENT,

    /** No TV paired/selected: the user has to open the app. */
    NO_TV,

    /** The TV needs pairing again, which only the app can do. */
    NEEDS_PAIRING,

    /** Could not get a session within the timeout. */
    UNREACHABLE,

    /** Connected, but this TV does not support the key. */
    UNSUPPORTED,
}

/**
 * Delivers one widget button press: reuses the live session, or connects the target TV and
 * waits up to [connectTimeoutMs] for it. Never holds anything open itself; [onUsed] lets the
 * connection lifecycle schedule the background disconnect.
 */
class WidgetKeySender(
    private val controller: RemoteController,
    private val devices: DeviceRepository,
    private val connect: (TvDevice) -> Unit = controller::connect,
    private val onUsed: () -> Unit = {},
    private val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
) {
    suspend fun send(key: RemoteKey): WidgetKeyOutcome {
        val connected = when (val state = controller.state.value) {
            is ConnectionState.Connected -> state
            else -> {
                val target = preferredDevice(state, devices.devices.first(), devices.lastSelectedId.first())
                    ?: return WidgetKeyOutcome(WidgetKeyResult.NO_TV, null)
                if (state is ConnectionState.PairingRequired && state.device.id == target.id) {
                    return WidgetKeyOutcome(WidgetKeyResult.NEEDS_PAIRING, target)
                }
                if (state is ConnectionState.Reconnecting && state.device.id == target.id) {
                    controller.retry() // ends the backoff wait
                } else if (!(state is ConnectionState.Connecting && state.device.id == target.id)) {
                    connect(target)
                }
                val settled = withTimeoutOrNull(connectTimeoutMs) {
                    controller.state.first {
                        it.device?.id == target.id &&
                            (it is ConnectionState.Connected || it is ConnectionState.PairingRequired)
                    }
                }
                onUsed()
                when (settled) {
                    is ConnectionState.Connected -> settled
                    is ConnectionState.PairingRequired -> return WidgetKeyOutcome(WidgetKeyResult.NEEDS_PAIRING, target)
                    else -> return WidgetKeyOutcome(WidgetKeyResult.UNREACHABLE, target)
                }
            }
        }
        onUsed()
        if (!connected.capabilities.supports(key)) return WidgetKeyOutcome(WidgetKeyResult.UNSUPPORTED, connected.device)
        controller.send(RemoteCommand.Key(key))
        return WidgetKeyOutcome(WidgetKeyResult.SENT, connected.device)
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 4_000L
    }
}
