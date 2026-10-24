package com.techport.tvremote.domain.model

/** Connection lifecycle. One state at a time; no loose booleans. */
sealed interface ConnectionState {
    /** No device selected. */
    data object Idle : ConnectionState

    data class Connecting(val device: TvDevice) : ConnectionState

    data class Connected(val device: TvDevice, val capabilities: RemoteCapabilities) : ConnectionState

    data class Reconnecting(
        val device: TvDevice,
        val attempt: Int,
        val lastError: RemoteError?,
    ) : ConnectionState

    /** The device needs (re-)pairing before it will accept a session. */
    data class PairingRequired(val device: TvDevice, val reason: RemoteError?) : ConnectionState

    /** Stopped trying. [error] is null when the user disconnected deliberately. */
    data class Disconnected(val device: TvDevice, val error: RemoteError?) : ConnectionState
}

val ConnectionState.device: TvDevice?
    get() = when (this) {
        ConnectionState.Idle -> null
        is ConnectionState.Connecting -> device
        is ConnectionState.Connected -> device
        is ConnectionState.Reconnecting -> device
        is ConnectionState.PairingRequired -> device
        is ConnectionState.Disconnected -> device
    }

/** Failure categories. Technical detail goes to logs; the UI maps these to plain language. */
enum class RemoteError {
    /** Phone is not on Wi-Fi/Ethernet. */
    NO_NETWORK,

    /** Android 17+ local network permission is not granted. */
    LOCAL_NETWORK_PERMISSION,

    /** Nothing answered at the address (TV off, asleep, different network). */
    UNREACHABLE,
    TIMEOUT,

    /** An established session dropped. */
    CONNECTION_LOST,

    /** The TV refused our credentials, or the pairing code was wrong. */
    PAIRING_REJECTED,

    /** The TV presented a different certificate from the one pinned at pairing. */
    CERTIFICATE_CHANGED,
    PROTOCOL_ERROR,
    UNSUPPORTED,
    UNKNOWN,
}

/** Thrown by protocol adapters; carries a [RemoteError] and keeps the cause for logs. */
class RemoteException(val error: RemoteError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: error.name, cause)
