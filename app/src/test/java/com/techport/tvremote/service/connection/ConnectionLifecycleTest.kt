package com.techport.tvremote.service.connection

import com.techport.tvremote.core.connectivity.NetworkStatus
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.domain.remote.RemoteController
import com.techport.tvremote.protocol.core.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionLifecycleTest {

    private val tv = TvDevice("tv1", "Living Room", "192.168.1.20", 6466, "mock")
    private val controller = FakeController()
    private val network = MutableStateFlow(NetworkStatus(true, "wifi-1"))
    private var wakeUps = 0
    private var preferredConnects = 0
    private val job = SupervisorJob()

    @After
    fun tearDown() = job.cancel()

    private fun TestScope.lifecycle() = ConnectionLifecycle(
        controller = controller,
        scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job),
        networkStatus = network,
        onNetworkAvailable = { wakeUps++ },
        connectPreferred = { preferredConnects++ },
        gracePeriodMs = 30_000,
    )

    private fun connected() = ConnectionState.Connected(tv, RemoteCapabilities.NONE)

    @Test
    fun backgroundDisconnectsOnlyAfterGracePeriod() = runTest {
        val lc = lifecycle()
        lc.onForeground()
        controller.state.value = connected()
        lc.onBackground()
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, controller.disconnects)
    }

    @Test
    fun returningWithinGracePeriodKeepsSession() = runTest {
        val lc = lifecycle()
        controller.state.value = connected()
        lc.onBackground()
        advanceTimeBy(10_000)
        lc.onForeground()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, controller.disconnects)
        assertTrue(controller.connects.isEmpty())
    }

    @Test
    fun foregroundReconnectsParkedDeviceImmediately() = runTest {
        val lc = lifecycle()
        controller.state.value = connected()
        lc.onBackground()
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(ConnectionState.Disconnected(tv, null), controller.state.value)
        lc.onForeground()
        assertEquals(listOf(tv), controller.connects)
    }

    @Test
    fun userDisconnectIsNotUndoneOnForeground() = runTest {
        val lc = lifecycle()
        controller.state.value = ConnectionState.Disconnected(tv, null)
        lc.onBackground()
        advanceTimeBy(30_001)
        runCurrent()
        lc.onForeground()
        assertEquals(0, controller.disconnects)
        assertTrue(controller.connects.isEmpty())
        assertEquals(0, controller.retries)
    }

    @Test
    fun foregroundWithNothingSelectedRunsAutoConnect() = runTest {
        val lc = lifecycle()
        lc.onForeground()
        runCurrent()
        assertEquals(1, preferredConnects)
    }

    @Test
    fun foregroundRetriesRecoverableFailureButNotPairingProblems() = runTest {
        val lc = lifecycle()
        controller.state.value = ConnectionState.Disconnected(tv, RemoteError.TIMEOUT)
        lc.onForeground()
        assertEquals(1, controller.retries)

        lc.onBackground()
        controller.state.value = ConnectionState.Disconnected(tv, RemoteError.CERTIFICATE_CHANGED)
        lc.onForeground()
        assertEquals(1, controller.retries)
    }

    @Test
    fun foregroundWakesBackoffWhileReconnecting() = runTest {
        val lc = lifecycle()
        controller.state.value = ConnectionState.Reconnecting(tv, 3, RemoteError.UNREACHABLE)
        lc.onForeground()
        assertEquals(1, wakeUps)
    }

    @Test
    fun widgetUseInBackgroundRearmsTimerFromLastPress() = runTest {
        val lc = lifecycle()
        lc.onBackground()
        advanceTimeBy(31_000)
        runCurrent()
        controller.state.value = ConnectionState.Disconnected(tv, null)

        lc.connectForBackgroundUse(tv)
        assertEquals(listOf(tv), controller.connects)
        controller.state.value = connected()
        advanceTimeBy(20_000)
        lc.noteBackgroundUse()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals("timer restarted by the second press", 0, controller.disconnects)
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(1, controller.disconnects)
    }

    @Test
    fun widgetUseInForegroundDoesNotScheduleDisconnect() = runTest {
        val lc = lifecycle()
        lc.onForeground()
        controller.state.value = connected()
        lc.noteBackgroundUse()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(0, controller.disconnects)
    }

    @Test
    fun networkReturnWakesReconnectAndIgnoresInitialValue() = runTest {
        val lc = lifecycle()
        lc.onForeground()
        controller.state.value = ConnectionState.Reconnecting(tv, 2, RemoteError.CONNECTION_LOST)
        lc.startNetworkWatch()
        runCurrent()
        assertEquals("initial status is not a change", 0, wakeUps)

        network.value = NetworkStatus.NONE
        runCurrent()
        assertEquals(0, wakeUps)
        network.value = NetworkStatus(true, "wifi-1")
        runCurrent()
        assertEquals(1, wakeUps)
        // Wi-Fi to Wi-Fi
        network.value = NetworkStatus(true, "wifi-2")
        runCurrent()
        assertEquals(2, wakeUps)
    }

    @Test
    fun networkReturnRetriesGivenUpConnectionOnlyInForeground() = runTest {
        val lc = lifecycle()
        lc.startNetworkWatch()
        runCurrent()
        controller.state.value = ConnectionState.Disconnected(tv, RemoteError.UNREACHABLE)

        lc.onBackground()
        network.value = NetworkStatus(true, "wifi-2")
        runCurrent()
        assertEquals(0, controller.retries)

        lc.onForeground() // retries once on its own
        assertEquals(1, controller.retries)
        network.value = NetworkStatus(true, "wifi-3")
        runCurrent()
        assertEquals(2, controller.retries)
        assertFalse(controller.retries > 2)
    }
}

/** Records calls; the test drives [state] by hand. */
internal class FakeController : RemoteController {
    override val state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val volume: StateFlow<VolumeState?> = MutableStateFlow(null)
    override val currentApp: StateFlow<String?> = MutableStateFlow(null)
    override val textField: StateFlow<TvTextField?> = MutableStateFlow(null)

    val connects = mutableListOf<TvDevice>()
    val sent = mutableListOf<RemoteCommand>()
    var disconnects = 0
    var retries = 0

    /** Optional reaction to connect(), e.g. move to Connected. */
    var onConnect: (TvDevice) -> Unit = {}

    override fun connect(device: TvDevice) {
        connects += device
        onConnect(device)
    }

    override fun retry() {
        retries++
    }

    override fun disconnect() {
        disconnects++
        state.value = when (val s = state.value) {
            is ConnectionState.Connected -> ConnectionState.Disconnected(s.device, null)
            is ConnectionState.Connecting -> ConnectionState.Disconnected(s.device, null)
            is ConnectionState.Reconnecting -> ConnectionState.Disconnected(s.device, null)
            else -> s
        }
    }

    override fun send(command: RemoteCommand) {
        sent += command
    }

    override suspend fun measureLatency(): Long? = null
    override suspend fun startVoice(): VoiceSession? = null
}
