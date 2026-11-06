package com.techport.tvremote.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.techport.tvremote.R
import com.techport.tvremote.core.connectivity.NetworkStatus
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.testing.InMemoryAppShortcutRepository
import com.techport.tvremote.testing.InMemorySettingsRepository
import com.techport.tvremote.testing.MockTvHarness
import com.techport.tvremote.testing.str
import com.techport.tvremote.testing.waitFor
import com.techport.tvremote.ui.remote.RemoteScreen
import com.techport.tvremote.ui.remote.RemoteViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionErrorTest {
    @get:Rule
    val compose = createComposeRule()

    private val tv = MockTvHarness(autoReconnect = false)

    @After
    fun tearDown() = tv.close()

    @Test
    fun unreachableTvShowsErrorAndTryAgainRecovers() {
        tv.mock.failNextConnects.set(1)
        val vm = RemoteViewModel(
            controller = tv.manager,
            devices = tv.devices,
            networkStatus = flowOf(NetworkStatus(true, "test")),
            settings = InMemorySettingsRepository().state,
            shortcuts = MutableStateFlow(emptyList()),
            shortcutRepository = InMemoryAppShortcutRepository(),
        )
        compose.setContent { RemoteScreen(vm, onOpenDevices = {}, onOpenKeyboard = {}, onOpenApps = {}) }
        tv.connect()
        compose.waitFor { tv.manager.state.value is ConnectionState.Disconnected }

        // On API 37 without the local network grant the banner explains that instead; both offer Try again.
        compose.onNodeWithText(str(R.string.action_try_again)).performClick()
        compose.waitFor { tv.isConnected }
        compose.onNodeWithText(str(R.string.action_try_again)).assertDoesNotExist()
    }
}
