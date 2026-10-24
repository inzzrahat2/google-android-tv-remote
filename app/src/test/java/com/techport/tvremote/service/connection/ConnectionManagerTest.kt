package com.techport.tvremote.service.connection

import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.repository.DeviceRepository
import com.techport.tvremote.protocol.core.ProtocolRegistry
import com.techport.tvremote.protocol.mock.MockTvProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerTest {

    private val device = TvDevice("tv1", "Living Room", "simulated", 0, MockTvProtocol.ID)
    private val protocol = MockTvProtocol(latencyMs = 10)
    private val repo = FakeDeviceRepository(listOf(device))

    private val managerJob = SupervisorJob()

    @After
    fun tearDown() = managerJob.cancel()

    // Not backgroundScope: advanceUntilIdle() ignores background work, and these tests
    // need the manager's coroutines to run on the virtual clock like foreground ones.
    private fun TestScope.manager(autoReconnect: Boolean = true) = ConnectionManager(
        scope = CoroutineScope(StandardTestDispatcher(testScheduler) + managerJob),
        registry = ProtocolRegistry(listOf(protocol)),
        devices = repo,
        logger = AppLogger(),
        autoReconnect = { autoReconnect },
        policy = ReconnectPolicy(initialDelayMs = 100, maxDelayMs = 1_000, maxAttempts = 3, jitterRatio = 0.0),
        clock = { testScheduler.currentTime },
    )

    @Test
    fun connectReachesConnectedAndRemembersDevice() = runTest {
        val m = manager()
        m.connect(device)
        advanceUntilIdle()
        assertTrue(m.state.value is ConnectionState.Connected)
        assertEquals("tv1", repo.lastSelected.value)
        assertTrue(repo.list.value.single().lastConnectedAt != null)
    }

    @Test
    fun commandsAreDeliveredInOrder() = runTest {
        val m = manager()
        m.connect(device)
        advanceUntilIdle()
        m.send(RemoteCommand.Key(RemoteKey.DPAD_UP))
        m.send(RemoteCommand.Key(RemoteKey.DPAD_CENTER, KeyAction.DOWN))
        m.send(RemoteCommand.Key(RemoteKey.DPAD_CENTER, KeyAction.UP))
        advanceUntilIdle()
        val received = protocol.activeSession.value!!.received.value
        assertEquals(
            listOf(
                RemoteCommand.Key(RemoteKey.DPAD_UP),
                RemoteCommand.Key(RemoteKey.DPAD_CENTER, KeyAction.DOWN),
                RemoteCommand.Key(RemoteKey.DPAD_CENTER, KeyAction.UP),
            ),
            received,
        )
        assertEquals(3, m.metrics.value.commandsSent)
    }

    @Test
    fun volumeIsMirroredFromSession() = runTest {
        val m = manager()
        m.connect(device)
        advanceUntilIdle()
        val before = m.volume.value!!.level
        m.send(RemoteCommand.Key(RemoteKey.VOLUME_UP))
        advanceUntilIdle()
        assertEquals(before + 2, m.volume.value!!.level)
    }

    @Test
    fun droppedSessionReconnectsAutomatically() = runTest {
        val m = manager()
        m.connect(device)
        advanceUntilIdle()
        val first = protocol.activeSession.value!!
        first.drop()
        runCurrent()
        advanceUntilIdle()
        assertTrue(m.state.value is ConnectionState.Connected)
        assertTrue(protocol.activeSession.value !== first)
    }

    @Test
    fun failuresBackOffThenGiveUp() = runTest {
        protocol.failNextConnects.set(10)
        val m = manager()
        m.connect(device)
        runCurrent()
        advanceTimeBy(25)
        val state = m.state.value
        assertTrue("expected Reconnecting, was $state", state is ConnectionState.Reconnecting)
        assertEquals(RemoteError.UNREACHABLE, (state as ConnectionState.Reconnecting).lastError)
        advanceUntilIdle()
        val end = m.state.value
        assertTrue("expected Disconnected, was $end", end is ConnectionState.Disconnected)
        assertEquals(RemoteError.UNREACHABLE, (end as ConnectionState.Disconnected).error)
    }

    @Test
    fun recoversAfterTransientFailures() = runTest {
        protocol.failNextConnects.set(2)
        val m = manager()
        m.connect(device)
        advanceUntilIdle()
        assertTrue(m.state.value is ConnectionState.Connected)
    }

    @Test
    fun networkReturnCutsBackoffShort() = runTest {
        protocol.failNextConnects.set(1)
        val m = manager()
        m.connect(device)
        runCurrent()
        advanceTimeBy(25) // first attempt failed, now waiting 100ms
        m.onNetworkAvailable()
        advanceTimeBy(30) // well under the backoff delay
        assertTrue(m.state.value is ConnectionState.Connected)
    }

    @Test
    fun revokedPairingRequiresPairing() = runTest {
        protocol.revokePairing(device.id)
        val m = manager()
        m.connect(device)
        advanceUntilIdle()
        assertTrue(m.state.value is ConnectionState.PairingRequired)
    }

    @Test
    fun noAutoReconnectStopsAfterDrop() = runTest {
        val m = manager(autoReconnect = false)
        m.connect(device)
        advanceUntilIdle()
        protocol.activeSession.value!!.drop()
        advanceUntilIdle()
        val state = m.state.value
        assertTrue(state is ConnectionState.Disconnected)
        assertEquals(RemoteError.CONNECTION_LOST, (state as ConnectionState.Disconnected).error)
    }

    @Test
    fun disconnectIsDeliberateAndDropsCommands() = runTest {
        val m = manager()
        m.connect(device)
        advanceUntilIdle()
        val session = protocol.activeSession.value!!
        m.disconnect()
        advanceUntilIdle()
        assertEquals(ConnectionState.Disconnected(device, null), m.state.value)
        m.send(RemoteCommand.Key(RemoteKey.HOME))
        advanceUntilIdle()
        assertTrue(session.received.value.isEmpty())
    }

    @Test
    fun switchingDevicesClosesPreviousSession() = runTest {
        val other = device.copy(id = "tv2", name = "Bedroom")
        repo.list.value = listOf(device, other)
        val m = manager()
        m.connect(device)
        advanceUntilIdle()
        val first = protocol.activeSession.value!!
        m.connect(other)
        advanceUntilIdle()
        assertEquals(other, (m.state.value as ConnectionState.Connected).device)
        // A closed session rejects commands.
        assertTrue(runCatching { first.execute(RemoteCommand.Key(RemoteKey.HOME)) }.isFailure)
    }
}

private class FakeDeviceRepository(initial: List<TvDevice>) : DeviceRepository {
    val list = MutableStateFlow(initial)
    val lastSelected = MutableStateFlow<String?>(null)
    override val devices = list
    override val lastSelectedId = lastSelected.map { it }
    override suspend fun get(id: String) = list.value.find { it.id == id }
    override suspend fun upsert(device: TvDevice) {
        list.value = list.value.filterNot { it.id == device.id } + device
    }
    override suspend fun remove(id: String) {
        list.value = list.value.filterNot { it.id == id }
    }
    override suspend fun rename(id: String, name: String) = Unit
    override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
    override suspend fun move(id: String, delta: Int) = Unit
    override suspend fun markConnected(id: String, host: String, port: Int, at: Long) {
        list.value = list.value.map { if (it.id == id) it.copy(lastConnectedAt = at) else it }
    }
    override suspend fun setLastSelected(id: String?) {
        lastSelected.value = id
    }
}
