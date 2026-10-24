package com.techport.tvremote.widget

import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.repository.DeviceRepository
import com.techport.tvremote.service.connection.FakeController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetKeySenderTest {
    private val living = TvDevice("tv1", "Living Room", "192.168.1.20", 6466, "mock")
    private val bedroom = TvDevice("tv2", "Bedroom", "192.168.1.21", 6466, "mock")
    private val allCaps = RemoteCapabilities(dpad = true, navigation = true, volume = true, mute = true, mediaControls = true)

    private val controller = FakeController()
    private val repo = FakeDevices(listOf(living, bedroom), lastSelected = "tv1")
    private var used = 0

    private fun sender(timeoutMs: Long = 4_000) =
        WidgetKeySender(controller, repo, onUsed = { used++ }, connectTimeoutMs = timeoutMs)

    @Test
    fun sendsDirectlyWhenConnected() = runTest {
        controller.state.value = ConnectionState.Connected(living, allCaps)
        val outcome = sender().send(RemoteKey.HOME)
        assertEquals(WidgetKeyResult.SENT, outcome.result)
        assertEquals(listOf(RemoteCommand.Key(RemoteKey.HOME)), controller.sent)
        assertTrue(controller.connects.isEmpty())
        assertTrue(used > 0)
    }

    @Test
    fun connectsLastSelectedTvThenSends() = runTest {
        controller.state.value = ConnectionState.Disconnected(living, null)
        val result = async { sender().send(RemoteKey.VOLUME_UP) }
        runCurrent()
        assertEquals(listOf(living), controller.connects)
        advanceTimeBy(800)
        controller.state.value = ConnectionState.Connected(living, allCaps)
        assertEquals(WidgetKeyResult.SENT, result.await().result)
        assertEquals(listOf(RemoteCommand.Key(RemoteKey.VOLUME_UP)), controller.sent)
    }

    @Test
    fun coldStartPicksLastSelectedFromRepository() = runTest {
        repo.last.value = "tv2"
        controller.onConnect = { controller.state.value = ConnectionState.Connected(it, allCaps) }
        val outcome = sender().send(RemoteKey.BACK)
        assertEquals(WidgetKeyResult.SENT, outcome.result)
        assertEquals(bedroom, outcome.device)
    }

    @Test
    fun givesUpAfterTimeoutWithoutSending() = runTest {
        controller.state.value = ConnectionState.Disconnected(living, RemoteError.UNREACHABLE)
        controller.onConnect = { controller.state.value = ConnectionState.Connecting(it) }
        val outcome = sender(timeoutMs = 4_000).send(RemoteKey.HOME)
        assertEquals(WidgetKeyResult.UNREACHABLE, outcome.result)
        assertEquals(4_000, testScheduler.currentTime)
        assertTrue(controller.sent.isEmpty())
        assertTrue("background timer must still be armed", used > 0)
    }

    @Test
    fun pairingRequiredIsReportedNotRetried() = runTest {
        controller.state.value = ConnectionState.PairingRequired(living, RemoteError.PAIRING_REJECTED)
        val outcome = sender().send(RemoteKey.HOME)
        assertEquals(WidgetKeyResult.NEEDS_PAIRING, outcome.result)
        assertTrue(controller.connects.isEmpty())
    }

    @Test
    fun staleStateOfAnotherTvDoesNotCountAsSettled() = runTest {
        repo.last.value = "tv2"
        controller.state.value = ConnectionState.Idle
        controller.onConnect = { controller.state.value = ConnectionState.PairingRequired(living, null) }
        val outcome = sender(timeoutMs = 1_000).send(RemoteKey.HOME)
        assertEquals(WidgetKeyResult.UNREACHABLE, outcome.result)
    }

    @Test
    fun reconnectingSameTvWakesBackoffInsteadOfReconnecting() = runTest {
        controller.state.value = ConnectionState.Reconnecting(living, 3, RemoteError.TIMEOUT)
        val result = async { sender().send(RemoteKey.HOME) }
        runCurrent()
        assertEquals(1, controller.retries)
        assertTrue(controller.connects.isEmpty())
        controller.state.value = ConnectionState.Connected(living, allCaps)
        assertEquals(WidgetKeyResult.SENT, result.await().result)
    }

    @Test
    fun noTvWhenNothingSavedOrSelected() = runTest {
        val empty = FakeDevices(emptyList(), lastSelected = null)
        val outcome = WidgetKeySender(controller, empty).send(RemoteKey.HOME)
        assertEquals(WidgetKeyResult.NO_TV, outcome.result)
        assertNull(outcome.device)
    }

    @Test
    fun unsupportedKeyIsNotSent() = runTest {
        controller.state.value = ConnectionState.Connected(living, RemoteCapabilities(navigation = true))
        val outcome = sender().send(RemoteKey.MEDIA_PLAY_PAUSE)
        assertEquals(WidgetKeyResult.UNSUPPORTED, outcome.result)
        assertTrue(controller.sent.isEmpty())
    }

    @Test
    fun targetPrefersCurrentThenLastThenOnly() {
        assertEquals(bedroom, widgetTarget(ConnectionState.Connecting(bedroom), listOf(living, bedroom), "tv1"))
        assertEquals(living, widgetTarget(ConnectionState.Idle, listOf(living, bedroom), "tv1"))
        assertEquals(living, widgetTarget(ConnectionState.Idle, listOf(living), null))
        assertNull(widgetTarget(ConnectionState.Idle, listOf(living, bedroom), null))
        assertNull(widgetTarget(ConnectionState.Idle, emptyList(), "tv1"))
    }
}

private class FakeDevices(initial: List<TvDevice>, lastSelected: String?) : DeviceRepository {
    val list = MutableStateFlow(initial)
    val last = MutableStateFlow(lastSelected)
    override val devices = list
    override val lastSelectedId = last
    override suspend fun get(id: String) = list.value.find { it.id == id }
    override suspend fun upsert(device: TvDevice) = Unit
    override suspend fun remove(id: String) = Unit
    override suspend fun rename(id: String, name: String) = Unit
    override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
    override suspend fun move(id: String, delta: Int) = Unit
    override suspend fun markConnected(id: String, host: String, port: Int, at: Long) = Unit
    override suspend fun setLastSelected(id: String?) {
        last.value = id
    }
}
