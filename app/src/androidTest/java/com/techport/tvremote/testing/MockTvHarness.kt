package com.techport.tvremote.testing

import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.protocol.core.ProtocolRegistry
import com.techport.tvremote.protocol.mock.MockTvProtocol
import com.techport.tvremote.protocol.mock.MockTvSession
import com.techport.tvremote.service.connection.ConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob

const val WAIT_MS = 5_000L

/**
 * The real [ConnectionManager] wired to the in-process [MockTvProtocol] on a real serial
 * dispatcher. Tests wait with `waitUntil {}` instead of virtual time. Call [close] in @After.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MockTvHarness(autoReconnect: Boolean = true) : AutoCloseable {
    val mock = MockTvProtocol(latencyMs = 5)
    val registry = ProtocolRegistry(listOf(mock))
    val devices = InMemoryDeviceRepository()
    val logger = AppLogger()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default.limitedParallelism(1))

    val manager = ConnectionManager(
        scope = scope,
        registry = registry,
        devices = devices,
        logger = logger,
        autoReconnect = { autoReconnect },
        connectTimeoutMs = { WAIT_MS },
    )

    val device = MockTvProtocol.DEMO_DEVICE.let {
        TvDevice(id = it.id, name = it.name, host = it.host, port = it.port, protocolId = it.protocolId, model = it.model)
    }

    val session: MockTvSession? get() = mock.activeSession.value

    /** Commands the simulated TV received in the current session. */
    fun received(): List<RemoteCommand> = session?.received?.value.orEmpty()

    fun connect() = manager.connect(device)

    val isConnected: Boolean get() = manager.state.value is ConnectionState.Connected

    override fun close() = job.cancel()
}

fun ComposeContentTestRule.waitFor(condition: () -> Boolean) = waitUntil(WAIT_MS, condition)

/** Connects [tv] and waits until the mock session is live. */
fun ComposeContentTestRule.connect(tv: MockTvHarness) {
    tv.connect()
    waitFor { tv.isConnected && tv.session != null }
    waitForIdle()
}

fun str(@StringRes id: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
