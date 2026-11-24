package com.techport.tvremote.ui.keyboard

import androidx.compose.ui.text.input.TextFieldValue
import com.techport.tvremote.core.clipboard.ClipboardReader
import com.techport.tvremote.domain.model.AppSettings
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.domain.remote.RemoteController
import com.techport.tvremote.protocol.core.VoiceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class FakeRemoteController(connected: Boolean = true, keyboard: Boolean = true) : RemoteController {
    val device = TvDevice("tv", "TV", "host", 0, "mock")
    val caps = RemoteCapabilities(dpad = true, keyboard = keyboard, voice = true)
    override val state = MutableStateFlow<ConnectionState>(
        if (connected) ConnectionState.Connected(device, caps) else ConnectionState.Idle,
    )
    override val volume = MutableStateFlow<VolumeState?>(null)
    override val textField = MutableStateFlow<TvTextField?>(null)
    val sent = mutableListOf<String>()
    var voice: VoiceSession? = null
    var voiceStarts = 0

    override fun connect(device: TvDevice) = Unit
    override fun retry() = Unit
    override fun disconnect() = Unit
    override fun send(command: RemoteCommand) {
        sent += describe(command)
    }
    override suspend fun measureLatency(): Long? = null
    override suspend fun startVoice(): VoiceSession? {
        voiceStarts++
        return voice
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardViewModelTest {

    private lateinit var main: TestDispatcher
    private val settings = MutableStateFlow(AppSettings(clipboardEnabled = true, sendOnEnter = true))
    private var clip: String? = "https://example.com"
    private val clipboard = ClipboardReader({ clip }, { settings.value.clipboardEnabled })

    @Before
    fun setUp() {
        main = StandardTestDispatcher()
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun type(vm: KeyboardViewModel, text: String) = vm.onValueChange(TextFieldValue(text))

    @Test
    fun typingIsMirroredAndEnterFlushesFirst() = runTest(main) {
        val tv = FakeRemoteController()
        val vm = KeyboardViewModel(tv, settings, clipboard)
        runCurrent()
        type(vm, "h")
        type(vm, "hi")
        vm.submit()
        assertEquals(listOf("T:h", "T:i", "ENTER"), tv.sent)
        assertEquals("", vm.input.value.text)
    }

    @Test
    fun inputIsIgnoredWhenNotConnectedOrUnsupported() = runTest(main) {
        val offline = FakeRemoteController(connected = false)
        val vm1 = KeyboardViewModel(offline, settings, clipboard)
        type(vm1, "x")
        vm1.pasteFromClipboard()
        assertTrue(offline.sent.isEmpty())

        val noIme = FakeRemoteController(keyboard = false)
        val vm2 = KeyboardViewModel(noIme, settings, clipboard)
        runCurrent()
        assertEquals(KeyboardAvailability.UNSUPPORTED, vm2.uiState.value.availability)
        type(vm2, "x")
        assertTrue(noIme.sent.isEmpty())
    }

    @Test
    fun clearDeletesOnlyWhatWeTyped() = runTest(main) {
        val tv = FakeRemoteController()
        val vm = KeyboardViewModel(tv, settings, clipboard)
        type(vm, "ab😀")
        tv.sent.clear()
        vm.clear()
        advanceTimeBy(500)
        assertEquals(listOf("DEL", "DEL", "DEL"), tv.sent)
    }

    @Test
    fun backspaceWithNothingTypedPressesDelOnTheTv() = runTest(main) {
        val tv = FakeRemoteController()
        val vm = KeyboardViewModel(tv, settings, clipboard)
        vm.backspace()
        assertEquals(listOf("DEL"), tv.sent)
    }

    @Test
    fun pasteSendsClipboardTextWhenEnabled() = runTest(main) {
        val tv = FakeRemoteController()
        val vm = KeyboardViewModel(tv, settings, clipboard)
        vm.pasteFromClipboard()
        assertEquals(listOf("T:https://example.com"), tv.sent)
        assertEquals("https://example.com", vm.input.value.text)
    }

    @Test
    fun pasteExplainsWhenDisabled() = runTest(main) {
        settings.value = settings.value.copy(clipboardEnabled = false)
        val tv = FakeRemoteController()
        val vm = KeyboardViewModel(tv, settings, clipboard)
        vm.pasteFromClipboard()
        assertTrue(tv.sent.isEmpty())
        assertEquals(KeyboardMessage.CLIPBOARD_DISABLED, vm.message.value)
    }

    @Test
    fun newTvFieldResetsTheBaselineAndPrefillsItsText() = runTest(main) {
        val tv = FakeRemoteController()
        val vm = KeyboardViewModel(tv, settings, clipboard)
        runCurrent()
        type(vm, "abc")
        tv.textField.value = TvTextField("old", "Search")
        runCurrent()
        assertEquals("old", vm.input.value.text)
        tv.sent.clear()
        vm.backspace()
        advanceTimeBy(100)
        // One DEL for the "d" of "old"; nothing aimed at the "abc" typed into the previous field.
        assertEquals(listOf("DEL"), tv.sent)
    }

    @Test
    fun disconnectClearsTheField() = runTest(main) {
        val tv = FakeRemoteController()
        val vm = KeyboardViewModel(tv, settings, clipboard)
        runCurrent()
        type(vm, "abc")
        tv.state.value = ConnectionState.Disconnected(tv.device, null)
        runCurrent()
        assertEquals("", vm.input.value.text)
    }

    @Test
    fun imeActionRespectsSendOnEnter() = runTest(main) {
        settings.value = settings.value.copy(sendOnEnter = false)
        val tv = FakeRemoteController()
        val vm = KeyboardViewModel(tv, settings, clipboard)
        type(vm, "a")
        type(vm, "ab")
        assertEquals(false, vm.onImeAction())
        assertEquals(listOf("T:a", "T:b"), tv.sent)
    }
}
