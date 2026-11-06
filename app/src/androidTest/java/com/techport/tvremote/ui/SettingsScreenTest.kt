package com.techport.tvremote.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.HapticStrength
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.ThemeMode
import com.techport.tvremote.testing.InMemorySettingsRepository
import com.techport.tvremote.testing.str
import com.techport.tvremote.testing.waitFor
import com.techport.tvremote.ui.settings.SettingsScreen
import com.techport.tvremote.ui.settings.SettingsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val repo = InMemorySettingsRepository()

    private fun show() {
        val vm = SettingsViewModel(repo, repo.state)
        compose.setContent { SettingsScreen(vm, showDebug = false, onOpenDebug = {}) }
    }

    @Test
    fun choosingThemeAndHapticsUpdatesSettings() {
        show()
        compose.onNodeWithText(str(R.string.settings_theme_amoled)).performScrollTo().performClick()
        compose.waitFor { repo.state.value.themeMode == ThemeMode.AMOLED }
        compose.onNodeWithText(str(R.string.settings_haptics_strong)).performScrollTo().performClick()
        compose.waitFor { repo.state.value.haptics == HapticStrength.STRONG }
    }

    @Test
    fun customizeRemoteHidesPower() {
        show()
        compose.onNodeWithText(str(R.string.settings_customize_buttons)).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.key_power)).performClick()
        compose.waitFor { RemoteKey.POWER in repo.state.value.hiddenKeys }
    }
}
