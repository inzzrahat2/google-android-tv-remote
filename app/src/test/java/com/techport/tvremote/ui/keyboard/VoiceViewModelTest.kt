package com.techport.tvremote.ui.keyboard

import com.techport.tvremote.core.voice.PcmSource
import com.techport.tvremote.core.voice.VoiceRecorder
import com.techport.tvremote.protocol.core.VoiceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {

    private lateinit var main: TestDispatcher

    private class Session : VoiceSession {
        var bytes = 0
        var ended = 0
        override suspend fun send(pcm: ByteArray) {
            bytes += pcm.size
        }
        override suspend fun end() {
            ended++
        }
    }

    /** Returns 2 bytes per read and yields the thread, like a real blocking mic read. */
    private val silence = object : PcmSource {
        var released = 0
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            Thread.yield()
            return minOf(2, length)
        }
        override fun release() {
            released++
        }
    }

    @Before
    fun setUp() {
        main = StandardTestDispatcher()
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun unsupportedTvShowsAMessage() = runTest(main) {
        val tv = FakeRemoteController().apply { voice = null }
        val vm = VoiceViewModel(tv, VoiceRecorder({ silence }, main))
        vm.onPressStart()
        advanceUntilIdle()
        vm.onPressEnd(held = true)
        assertEquals(VoiceMessage.UNSUPPORTED, vm.state.value.message)
        assertEquals(VoicePhase.IDLE, vm.state.value.phase)
    }

    @Test
    fun holdToTalkStopsOnRelease() = runTest(main) {
        val session = Session()
        val tv = FakeRemoteController().apply { voice = session }
        // Stop as soon as recording starts so the blocking loop ends deterministically.
        val vm = VoiceViewModel(tv, VoiceRecorder({ silence }, main, chunkBytes = 4))
        vm.onPressStart()
        assertEquals(VoicePhase.STARTING, vm.state.value.phase)
        vm.onPressEnd(held = true)
        assertEquals(VoicePhase.FINISHING, vm.state.value.phase)
        advanceUntilIdle()
        assertEquals(VoicePhase.IDLE, vm.state.value.phase)
        assertEquals(1, session.ended)
        assertEquals(1, silence.released)
        assertEquals(null, vm.state.value.message)
    }

    @Test
    fun tapStartsHandsFreeAndSecondTapStops() = runTest(main) {
        val session = Session()
        val tv = FakeRemoteController().apply { voice = session }
        val vm = VoiceViewModel(tv, VoiceRecorder({ silence }, main, chunkBytes = 4))
        vm.onPressStart()
        vm.onPressEnd(held = false)
        assertTrue(vm.state.value.handsFree)
        vm.onPressStart() // second tap
        vm.onPressEnd(held = false)
        runCurrent()
        advanceUntilIdle()
        assertEquals(VoicePhase.IDLE, vm.state.value.phase)
        assertEquals(1, tv.voiceStarts)
        assertEquals(1, session.ended)
    }
}
