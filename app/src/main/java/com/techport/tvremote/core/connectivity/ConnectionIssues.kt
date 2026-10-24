package com.techport.tvremote.core.connectivity

import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteError

/**
 * Pure rules that combine the connection state machine with the phone's network.
 *
 * ConnectionManager only knows that a socket failed. These rules explain *why* in terms the
 * user can act on (not on Wi-Fi, permission missing, TV on another network) and decide when a
 * network change is worth an immediate retry.
 */
object ConnectionIssues {

    /**
     * The error the UI should show for [state], or null when there is nothing to report.
     *
     * Priority while not connected: no local network, then a missing Android 17 local network
     * permission, then whatever the connection itself reported. A deliberate disconnect
     * (`Disconnected(error = null)`) and pairing problems are left as they are: they are not
     * caused by the network. A live session is never second-guessed.
     */
    fun uiError(
        state: ConnectionState,
        network: NetworkStatus,
        localNetworkPermissionGranted: Boolean = true,
    ): RemoteError? {
        val stateError = when (state) {
            ConnectionState.Idle, is ConnectionState.Connected -> return null
            is ConnectionState.PairingRequired -> return state.reason
            is ConnectionState.Disconnected -> state.error ?: return null
            is ConnectionState.Connecting -> null
            is ConnectionState.Reconnecting -> state.lastError
        }
        return when {
            !network.isLocalNetworkAvailable -> RemoteError.NO_NETWORK
            !localNetworkPermissionGranted -> RemoteError.LOCAL_NETWORK_PERMISSION
            else -> stateError
        }
    }

    /**
     * True when the TV's saved address is an IPv4 literal outside every subnet the phone is on,
     * and the connection is failing to reach it. That usually means the phone joined a different
     * Wi-Fi (guest network, mesh/extender with its own subnet) or the router handed out new
     * addresses. False when unknown (host names, no subnet info, no network).
     */
    fun likelyDifferentNetwork(state: ConnectionState, network: NetworkStatus): Boolean {
        val (host, error) = when (state) {
            is ConnectionState.Reconnecting -> state.device.host to state.lastError
            is ConnectionState.Disconnected -> state.device.host to state.error
            else -> return false
        }
        if (error !in REACHABILITY_ERRORS) return false
        if (!network.isLocalNetworkAvailable || network.subnets.isEmpty()) return false
        if (Ipv4Subnet.parseIpv4(host) == null) return false
        return network.subnets.none { it.containsHost(host) }
    }

    /**
     * Whether a change from [previous] to [current] should cut a reconnect backoff short:
     * a local network appeared (Wi-Fi back, airplane mode off, router back up) or the phone
     * moved to a different local network (Wi-Fi to Wi-Fi).
     */
    fun shouldRetryAfterNetworkChange(previous: NetworkStatus?, current: NetworkStatus): Boolean {
        if (!current.isLocalNetworkAvailable) return false
        if (previous == null || !previous.isLocalNetworkAvailable) return true
        return previous.networkId != current.networkId
    }

    /**
     * Errors that a new attempt can fix on its own. Pairing problems need the user, and an
     * unsupported protocol never changes.
     */
    fun isRecoverable(error: RemoteError?): Boolean = error != null && error !in PERMANENT_ERRORS

    private val REACHABILITY_ERRORS = setOf(RemoteError.UNREACHABLE, RemoteError.TIMEOUT, RemoteError.CONNECTION_LOST)
    private val PERMANENT_ERRORS = setOf(
        RemoteError.PAIRING_REJECTED,
        RemoteError.CERTIFICATE_CHANGED,
        RemoteError.UNSUPPORTED,
    )
}
