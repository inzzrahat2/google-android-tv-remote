package com.techport.tvremote.ui.apps

import com.techport.tvremote.data.apps.AppLinkSuggestions
import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.domain.remote.RemoteController
import com.techport.tvremote.domain.repository.AppShortcutRepository
import com.techport.tvremote.protocol.core.VoiceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {
    private val tv = TvDevice("1", "TV", "10.0.0.1", 6466, "mock")
    private lateinit var repo: FakeShortcutRepository
    private lateinit var controller: FakeController
    private lateinit var vm: AppsViewModel
    private var ids = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeShortcutRepository()
        controller = FakeController()
        vm = AppsViewModel(repo, controller, clock = { 1_000L }, newId = { "id${++ids}" })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun connect(appLaunch: Boolean) {
        controller.state.value = ConnectionState.Connected(tv, RemoteCapabilities(dpad = true, appLaunch = appLaunch))
    }

    @Test
    fun launchSendsLinkAndMarksUsedWhenReady() = runTest {
        repo.upsert(AppShortcut("a", "YouTube", "https://www.youtube.com"))
        connect(appLaunch = true)
        assertTrue(vm.launch(repo.list.value.single()))
        assertEquals(listOf(RemoteCommand.LaunchApp("https://www.youtube.com")), controller.sent)
        assertEquals(1_000L, repo.get("a")!!.lastUsedAt)
    }

    @Test
    fun launchIsRefusedWhenDisconnectedOrUnsupported() = runTest {
        val app = AppShortcut("a", "YouTube", "https://www.youtube.com")
        repo.upsert(app)
        assertFalse(vm.launch(app))
        connect(appLaunch = false)
        assertFalse(vm.launch(app))
        assertTrue(controller.sent.isEmpty())
        assertNull(repo.get("a")!!.lastUsedAt)
    }

    @Test
    fun saveValidatesThenCreatesOrUpdates() = runTest {
        assertEquals(ShortcutInputError.LINK_INVALID, vm.save(null, "Bad", "not a link"))
        assertTrue(repo.list.value.isEmpty())

        assertNull(vm.save(null, " Plex ", " https://app.plex.tv "))
        val created = repo.list.value.single()
        assertEquals(AppShortcut("id1", "Plex", "https://app.plex.tv"), created)

        repo.setFavorite("id1", true)
        assertNull(vm.save("id1", "Plex TV", "https://app.plex.tv/web"))
        val updated = repo.get("id1")!!
        assertEquals("Plex TV", updated.label)
        assertTrue("editing keeps favorite", updated.isFavorite)
    }

    @Test
    fun stateReflectsAvailabilityAndSuggestions() = runTest {
        val collected = mutableListOf<AppsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { collected += it } }
        assertEquals(LaunchAvailability.NOT_CONNECTED, vm.state.value.availability)
        assertEquals(AppLinkSuggestions.ALL.size, vm.state.value.suggestions.size)

        vm.addSuggestion(AppLinkSuggestions.ALL.first())
        connect(appLaunch = true)
        val state = vm.state.value
        assertEquals(LaunchAvailability.READY, state.availability)
        assertEquals(1, state.shortcuts.size)
        assertEquals(AppLinkSuggestions.ALL.size - 1, state.suggestions.size)
    }
}

private class FakeShortcutRepository : AppShortcutRepository {
    val list = MutableStateFlow<List<AppShortcut>>(emptyList())
    override val shortcuts: Flow<List<AppShortcut>> =
        list.map { l -> l.sortedWith(compareByDescending<AppShortcut> { it.isFavorite }.thenBy { it.sortOrder }) }

    override suspend fun get(id: String) = list.value.find { it.id == id }
    override suspend fun upsert(shortcut: AppShortcut) {
        val i = list.value.indexOfFirst { it.id == shortcut.id }
        list.value = if (i >= 0) list.value.toMutableList().also { it[i] = shortcut } else list.value + shortcut
    }
    override suspend fun remove(id: String) {
        list.value = list.value.filterNot { it.id == id }
    }
    override suspend fun setFavorite(id: String, favorite: Boolean) {
        list.value = list.value.map { if (it.id == id) it.copy(isFavorite = favorite) else it }
    }
    override suspend fun move(id: String, delta: Int) = Unit
    override suspend fun markUsed(id: String, at: Long) {
        list.value = list.value.map { if (it.id == id) it.copy(lastUsedAt = at) else it }
    }
}

private class FakeController : RemoteController {
    override val state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val volume: StateFlow<VolumeState?> = MutableStateFlow(null)
    override val currentApp: StateFlow<String?> = MutableStateFlow(null)
    override val textField: StateFlow<TvTextField?> = MutableStateFlow(null)
    val sent = mutableListOf<RemoteCommand>()

    override fun connect(device: TvDevice) = Unit
    override fun retry() = Unit
    override fun disconnect() = Unit
    override fun send(command: RemoteCommand) {
        sent += command
    }
    override suspend fun measureLatency(): Long? = null
    override suspend fun startVoice(): VoiceSession? = null
}
