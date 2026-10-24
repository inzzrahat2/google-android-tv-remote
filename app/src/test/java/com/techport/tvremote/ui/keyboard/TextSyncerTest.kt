package com.techport.tvremote.ui.keyboard

import com.techport.tvremote.domain.model.RemoteCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun describe(command: RemoteCommand) = when (command) {
    is RemoteCommand.Text -> "T:${command.text}"
    is RemoteCommand.Key -> command.key.name
    is RemoteCommand.LaunchApp -> "L"
}

@OptIn(ExperimentalCoroutinesApi::class)
class TextSyncerTest {

    private val sent = mutableListOf<String>()

    private fun TestScope.syncer() =
        TextSyncer(backgroundScope, { sent += describe(it) }, windowMs = 40, maxKeysPerStep = 4, bulkIntervalMs = 80)

    @Test
    fun firstKeystrokeIsSentImmediately() = runTest {
        val s = syncer()
        s.update("h")
        assertEquals(listOf("T:h"), sent)
    }

    @Test
    fun burstInsideWindowBecomesOneTextCommand() = runTest {
        val s = syncer()
        s.update("h")
        advanceTimeBy(10)
        s.update("he")
        advanceTimeBy(10)
        s.update("hel")
        advanceTimeBy(10)
        s.update("hell")
        assertEquals(listOf("T:h"), sent)
        advanceTimeBy(11)
        runCurrent()
        assertEquals(listOf("T:h", "T:ell"), sent)
        assertEquals("hell", s.sent)
        assertFalse(s.hasPending)
    }

    @Test
    fun afterAQuietWindowTheNextKeystrokeIsImmediateAgain() = runTest {
        val s = syncer()
        s.update("a")
        advanceTimeBy(100)
        runCurrent()
        s.update("ab")
        assertEquals(listOf("T:a", "T:b"), sent)
    }

    @Test
    fun editsWithinWindowAreDiffedAsAWhole() = runTest {
        val s = syncer()
        s.update("cat")
        s.update("ca")
        s.update("car")
        advanceTimeBy(41)
        runCurrent()
        // Net change "cat" -> "car": one DEL and one insert, not one command per keystroke.
        assertEquals(listOf("T:cat", "DEL", "T:r"), sent)
    }

    @Test
    fun longDeletionsArePacedToProtectTheCommandQueue() = runTest {
        val s = syncer()
        s.reset("abcdefghij")
        s.update("")
        assertEquals(4, sent.size)
        advanceTimeBy(81)
        runCurrent()
        assertEquals(8, sent.size)
        advanceTimeBy(81)
        runCurrent()
        assertEquals(List(10) { "DEL" }, sent)
        assertEquals("", s.sent)
    }

    @Test
    fun flushSendsEverythingNowInOrder() = runTest {
        val s = syncer()
        s.update("a")
        s.update("abc")
        s.flush()
        assertEquals(listOf("T:a", "T:bc"), sent)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, sent.size)
    }

    @Test
    fun resetDropsPendingWorkAndMovesTheBaseline() = runTest {
        val s = syncer()
        s.update("a")
        s.update("ab")
        s.reset("")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("T:a"), sent)
        assertTrue(s.sent.isEmpty())
    }
}
