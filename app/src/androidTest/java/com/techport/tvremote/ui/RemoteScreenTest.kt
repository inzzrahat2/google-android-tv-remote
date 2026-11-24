package com.techport.tvremote.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.techport.tvremote.R
import com.techport.tvremote.core.connectivity.NetworkStatus
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.ThemeMode
import com.techport.tvremote.testing.InMemoryAppShortcutRepository
import com.techport.tvremote.testing.InMemorySettingsRepository
import com.techport.tvremote.testing.MockTvHarness
import com.techport.tvremote.testing.connect
import com.techport.tvremote.testing.str
import com.techport.tvremote.testing.waitFor
import com.techport.tvremote.ui.remote.RemoteScreen
import com.techport.tvremote.ui.remote.RemoteViewModel
import com.techport.tvremote.ui.theme.TvRemoteTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val tv = MockTvHarness()
    private val settings = InMemorySettingsRepository()

    @After
    fun tearDown() = tv.close()

    private fun showRemote() {
        val vm = RemoteViewModel(
            controller = tv.manager,
            devices = tv.devices,
            networkStatus = flowOf(NetworkStatus(true, "test")),
            settings = settings.state,
            shortcuts = MutableStateFlow(emptyList()),
            shortcutRepository = InMemoryAppShortcutRepository(),
        )
        compose.setContent {
            TvRemoteTheme(ThemeMode.DARK, dynamicColor = false) {
                RemoteScreen(vm, onOpenDevices = {}, onOpenKeyboard = {}, onOpenApps = {})
            }
        }
        compose.connect(tv)
    }

    @Test
    fun dpadArrowsAndOkReachTheTv() {
        showRemote()
        // Accessibility click (what TalkBack uses) sends one CLICK; a touch on OK would send DOWN/UP.
        compose.onNodeWithContentDescription(str(R.string.key_up)).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription(str(R.string.key_ok)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitFor { tv.received().size >= 2 }
        val keys = tv.received().filterIsInstance<RemoteCommand.Key>().map { it.key }
        assert(keys == listOf(RemoteKey.DPAD_UP, RemoteKey.DPAD_CENTER)) { "got $keys" }
    }

    @Test
    fun volumeUpUpdatesShownLevel() {
        showRemote()
        compose.onNodeWithText("30%").assertExists()
        compose.onNodeWithContentDescription(str(R.string.key_volume_up)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitFor { tv.manager.volume.value?.level == 32 }
        compose.onNodeWithText("32%").assertExists()
    }

    @Test
    fun hiddenButtonsAreNotShown() {
        runBlocking { settings.update { it.copy(hiddenKeys = setOf(RemoteKey.MEDIA_PLAY_PAUSE)) } }
        showRemote()
        compose.onNodeWithContentDescription(str(R.string.key_play_pause)).assertDoesNotExist()
        compose.onNodeWithContentDescription(str(R.string.key_home)).assertExists()
    }

    @Test
    fun touchpadToggleShowsTouchpad() {
        showRemote()
        compose.onNodeWithText(str(R.string.remote_mode_touchpad)).performClick()
        compose.onNodeWithText(str(R.string.touchpad_hint)).assertExists()
        compose.onNodeWithContentDescription(str(R.string.key_up)).assertDoesNotExist()

        compose.onNodeWithText(str(R.string.remote_mode_dpad)).performClick()
        compose.onNodeWithContentDescription(str(R.string.key_up)).assertExists()
    }
}
