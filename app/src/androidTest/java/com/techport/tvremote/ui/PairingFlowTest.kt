package com.techport.tvremote.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.techport.tvremote.R
import com.techport.tvremote.core.discovery.DiscoveryService
import com.techport.tvremote.protocol.mock.MockTvProtocol
import com.techport.tvremote.testing.MockTvHarness
import com.techport.tvremote.testing.str
import com.techport.tvremote.testing.waitFor
import com.techport.tvremote.ui.devices.DevicesScreen
import com.techport.tvremote.ui.devices.DevicesViewModel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val tv = MockTvHarness()

    @After
    fun tearDown() = tv.close()

    @Test
    fun wrongCodeIsRejectedThenRightCodePairsAndConnects() {
        var openedRemote = false
        val vm = DevicesViewModel(
            devices = tv.devices,
            controller = tv.manager,
            discovery = DiscoveryService(tv.registry),
            protocols = tv.registry,
            forgetDevice = { tv.devices.remove(it.id) },
            logger = tv.logger,
        )
        compose.setContent { DevicesScreen(vm, onOpenRemote = { openedRemote = true }) }

        compose.waitFor { compose.onAllNodesWithText(MockTvProtocol.DEMO_DEVICE.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(str(R.string.action_pair)).onFirst().performClick()

        val codeField = hasSetTextAction()
        compose.waitFor { compose.onAllNodes(codeField).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(codeField).performTextInput("000000")
        compose.waitFor { compose.onAllNodesWithText(str(R.string.pairing_wrong_code)).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(codeField).performTextInput(MockTvProtocol.DEFAULT_CODE)
        compose.waitFor { tv.devices.current.any { it.id == MockTvProtocol.DEMO_DEVICE.id } }
        compose.waitFor { tv.isConnected && openedRemote }
        compose.onNodeWithText(str(R.string.pairing_wrong_code)).assertDoesNotExist()
    }
}
