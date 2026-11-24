package com.techport.tvremote.service.connection

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.techport.tvremote.core.connectivity.ConnectionIssues
import com.techport.tvremote.core.connectivity.NetworkStatus
import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.device
import com.techport.tvremote.domain.remote.RemoteController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Ties the TV session to the app being in use, so there is never a permanent background
 * connection and no foreground service.
 *
 * - App goes to the background: after [gracePeriodMs] (default 30 s) the session is closed and
 *   the device is remembered ("parked"). Switching apps briefly keeps the session.
 * - App comes back: the parked device is reconnected straight away; a connection that gave up
 *   with a recoverable error is retried; with nothing selected, [connectPreferred] runs.
 * - Widgets/tile used while in the background: [connectForBackgroundUse] and
 *   [noteBackgroundUse] (re)arm the same grace timer, so a widget press never keeps the app alive.
 * - Network came back or changed: a backoff wait is cut short; a connection that gave up is
 *   retried if the app is in the foreground.
 *
 * Thread-safe: lifecycle callbacks arrive on the main thread, the timer fires on [scope].
 * The timing uses [scope]'s clock (`delay`), so tests drive it with virtual time.
 */
class ConnectionLifecycle(
    private val controller: RemoteController,
    private val scope: CoroutineScope,
    private val networkStatus: Flow<NetworkStatus>,
    /** Cuts the reconnect backoff short (ConnectionManager.onNetworkAvailable). */
    private val onNetworkAvailable: () -> Unit,
    /** Connects the last used / only TV when nothing is selected (AutoConnector.connectPreferred). */
    private val connectPreferred: suspend () -> Unit,
    private val logger: AppLogger? = null,
    private val gracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS,
) {
    private val lock = Any()
    private var pendingDisconnect: Job? = null
    private var parked: TvDevice? = null
    private var networkJob: Job? = null

    private val _isInForeground = MutableStateFlow(false)

    private val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = onForeground()
        override fun onStop(owner: LifecycleOwner) = onBackground()
    }

    /**
     * Starts watching [processLifecycle] (ProcessLifecycleOwner.get().lifecycle) and the network.
     * Call once, on the main thread.
     */
    fun start(processLifecycle: Lifecycle) {
        // A process started for a widget, tile or receiver never reaches STARTED; arm the timer
        // so anything connected meanwhile is closed. ON_START cancels it for a normal launch.
        if (!processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onBackground()
        processLifecycle.addObserver(observer)
        startNetworkWatch()
    }

    /** Network handling only; [start] calls it. Exposed for tests. */
    fun startNetworkWatch() {
        synchronized(lock) {
            if (networkJob != null) return
            networkJob = scope.launch {
                var previous: NetworkStatus? = null
                networkStatus.collect { current ->
                    // The first value is the network at start-up, not a change.
                    if (previous != null && ConnectionIssues.shouldRetryAfterNetworkChange(previous, current)) {
                        onNetworkRecovered()
                    }
                    previous = current
                }
            }
        }
    }

    fun onForeground() {
        synchronized(lock) {
            _isInForeground.value = true
            pendingDisconnect?.cancel()
            pendingDisconnect = null
            val resume = parked
            parked = null
            val state = controller.state.value
            when {
                // Also covers the race where our own disconnect is still in flight.
                resume != null && state.device?.id == resume.id -> {
                    logger?.i(TAG, "Foreground: reconnecting ${resume.name}")
                    controller.connect(resume)
                }
                state is ConnectionState.Idle -> scope.launch { connectPreferred() }
                state is ConnectionState.Reconnecting -> onNetworkAvailable()
                state is ConnectionState.Disconnected && ConnectionIssues.isRecoverable(state.error) -> controller.retry()
                else -> Unit
            }
        }
    }

    fun onBackground() {
        synchronized(lock) {
            _isInForeground.value = false
            scheduleDisconnectLocked()
        }
    }

    /**
     * Connects [device] for a widget or tile action. In the background the grace timer is
     * (re)armed, so the session closes again [gracePeriodMs] after the last use.
     */
    fun connectForBackgroundUse(device: TvDevice) {
        synchronized(lock) {
            parked = null
            controller.connect(device)
            if (!_isInForeground.value) scheduleDisconnectLocked()
        }
    }

    /** A widget/tile used the session; restarts the grace period when in the background. */
    fun noteBackgroundUse() {
        synchronized(lock) {
            if (!_isInForeground.value) scheduleDisconnectLocked()
        }
    }

    private fun scheduleDisconnectLocked() {
        pendingDisconnect?.cancel()
        pendingDisconnect = scope.launch {
            delay(gracePeriodMs)
            parkAndDisconnect()
        }
    }

    private fun parkAndDisconnect() {
        synchronized(lock) {
            if (_isInForeground.value) return
            pendingDisconnect = null
            val state = controller.state.value
            val active = state is ConnectionState.Connected ||
                state is ConnectionState.Connecting ||
                state is ConnectionState.Reconnecting
            if (!active) return
            parked = state.device
            logger?.i(TAG, "Background grace period over: closing session")
            // Inside the lock so a concurrent onForeground() sees `parked` and reconnects after it.
            controller.disconnect()
        }
    }

    private fun onNetworkRecovered() {
        synchronized(lock) {
            when (val state = controller.state.value) {
                is ConnectionState.Reconnecting -> {
                    logger?.i(TAG, "Network changed: retrying now")
                    onNetworkAvailable()
                }
                is ConnectionState.Disconnected ->
                    if (_isInForeground.value && ConnectionIssues.isRecoverable(state.error)) {
                        logger?.i(TAG, "Network back: reconnecting")
                        controller.retry()
                    }
                else -> Unit
            }
        }
    }

    companion object {
        const val DEFAULT_GRACE_PERIOD_MS = 30_000L
        private const val TAG = "Lifecycle"
    }
}
