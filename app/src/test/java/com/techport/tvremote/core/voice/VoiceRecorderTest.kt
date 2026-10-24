package com.techport.tvremote.core.voice

import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.RemoteException
import com.techport.tvremote.protocol.core.VoiceSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeVoiceSession(private val failOnSend: Boolean = false) : VoiceSession {
    val chunks = mutableListOf<ByteArray>()
    var ended = 0

    override suspend fun send(pcm: ByteArray) {
        if (failOnSend) throw RemoteException(RemoteError.CONNECTION_LOST)
        chunks += pcm
    }

    override suspend fun end() {
        ended++
    }
}

/** Plays [script] (one entry per read; negative = error code), then calls [onExhausted] and reads 0 bytes. */
private class FakePcmSource(
    private val script: List<Int>,
    private val onRead: (Int) -> Unit = {},
    private val onExhausted: () -> Unit,
) : PcmSource {
    var reads = 0
    var released = 0
    private var next: Byte = 1

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        onRead(reads)
        val planned = script.getOrNull(reads++)
        if (planned == null) {
            onExhausted()
            return 0
        }
        if (planned < 0) return planned
        val n = minOf(planned, length)
        repeat(n) { buffer[offset + it] = next++ }
        return n
    }

    override fun release() {
        released++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRecorderTest {

    private val stop = AtomicBoolean(false)

    private fun TestScope.recorder(source: PcmSourceFactory, chunk: Int = 4, maxMs: Long = 60_000) =
        VoiceRecorder(source, StandardTestDispatcher(testScheduler), chunkBytes = chunk, maxDurationMs = maxMs)

    @Test
    fun streamsFullChunksThenTailThenEnds() = runTest {
        val source = FakePcmSource(listOf(3, 3, 3), onExhausted = { stop.set(true) })
        val session = FakeVoiceSession()
        val outcome = recorder({ source }).stream(session, stop)

        assertEquals(VoiceOutcome.SENT, outcome)
        // Reads fill the chunk up to its size (3, then 1 of the next 3), then 3 more as the tail.
        assertEquals(listOf(4, 3), session.chunks.map { it.size })
        assertArrayEquals((1..7).map { it.toByte() }.toByteArray(), session.chunks.reduce { a, b -> a + b })
        assertEquals(1, session.ended)
        assertEquals(1, source.released)
    }

    @Test
    fun stopsAtTheTimeLimit() = runTest {
        // 100 ms at 8 kHz/16-bit = 1,600 bytes.
        val source = FakePcmSource(List(100) { 1_600 }, onExhausted = {})
        val session = FakeVoiceSession()
        val outcome = recorder({ source }, chunk = 8_192, maxMs = 100).stream(session, stop)
        assertEquals(VoiceOutcome.SENT, outcome)
        assertEquals(1_600, session.chunks.sumOf { it.size })
        assertEquals(1, session.ended)
    }

    @Test
    fun missingPermissionEndsTheTvSessionAndReportsIt() = runTest {
        val session = FakeVoiceSession()
        val outcome = recorder({
            throw MicUnavailableException(MicUnavailableException.Reason.NO_PERMISSION, "no")
        }).stream(session, stop)
        assertEquals(VoiceOutcome.NO_PERMISSION, outcome)
        assertEquals(1, session.ended)
        assertTrue(session.chunks.isEmpty())
    }

    @Test
    fun micErrorSendsWhatWeHadAndReleases() = runTest {
        val source = FakePcmSource(listOf(2, -3), onExhausted = {})
        val session = FakeVoiceSession()
        val outcome = recorder({ source }).stream(session, stop)
        assertEquals(VoiceOutcome.MIC_FAILED, outcome)
        assertEquals(listOf(2), session.chunks.map { it.size })
        assertEquals(1, session.ended)
        assertEquals(1, source.released)
    }

    @Test
    fun sendFailureReleasesTheMic() = runTest {
        val source = FakePcmSource(listOf(4, 4), onExhausted = { stop.set(true) })
        val outcome = recorder({ source }).stream(FakeVoiceSession(failOnSend = true), stop)
        assertEquals(VoiceOutcome.SEND_FAILED, outcome)
        assertEquals(1, source.released)
    }

    @Test
    fun cancellationReleasesTheMicAndClosesTheUtterance() = runTest {
        val session = FakeVoiceSession()
        lateinit var source: FakePcmSource
        val job = launch {
            recorder({ source }).stream(session, stop)
        }
        source = FakePcmSource(List(100) { 4 }, onRead = { if (it == 2) job.cancel() }, onExhausted = {})
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertEquals(1, source.released)
        assertEquals(1, session.ended)
    }
}
