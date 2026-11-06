package com.techport.tvremote.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.techport.tvremote.core.clipboard.AndroidClipboardSource
import com.techport.tvremote.core.clipboard.ClipboardReader
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.testing.InMemorySettingsRepository
import com.techport.tvremote.testing.MockTvHarness
import com.techport.tvremote.testing.connect
import com.techport.tvremote.testing.waitFor
import com.techport.tvremote.ui.keyboard.KeyboardScreen
import com.techport.tvremote.ui.keyboard.KeyboardViewModel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val tv = MockTvHarness()

    @After
    fun tearDown() = tv.close()

    @Test
    fun typedTextReachesTheTv() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = KeyboardViewModel(
            controller = tv.manager,
            settings = InMemorySettingsRepository().state,
            clipboard = ClipboardReader(AndroidClipboardSource(context)) { false },
        )
        compose.setContent { KeyboardScreen(vm, onClose = {}) }
        compose.connect(tv)

        compose.onNode(hasSetTextAction()).performTextInput("hello")
        compose.waitFor {
            tv.received().filterIsInstance<RemoteCommand.Text>().joinToString("") { it.text } == "hello"
        }
    }
}
