package com.techport.tvremote.core.voice

import com.techport.tvremote.domain.model.VoiceFormat
import com.techport.tvremote.protocol.core.VoiceSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class VoiceOutcome {
    /** Audio was streamed and the utterance ended; the TV now handles the query. */
    SENT,

    /** RECORD_AUDIO is not granted. */
    NO_PERMISSION,

    /** The microphone is busy (call, other recorder) or can't capture at 8 kHz. */
    MIC_UNAVAILABLE,

    /** The microphone failed while recording. The utterance was ended with what we had. */
    MIC_FAILED,

    /** The TV connection failed while streaming. */
    SEND_FAILED,
}

/**
 * Streams microphone audio into a [VoiceSession].
 *
 * Audio is read on [io] in ~100 ms slices and handed to the session in [chunkBytes] chunks
 * (the adapter re-chunks to protocol limits). Recording stops when [stop] becomes true, after
 * [maxDurationMs], or on cancellation; the microphone is released in every case.
 *
 * Audio is never logged, written to disk or kept: the only copy leaves through [VoiceSession.send],
 * and the working buffer is zeroed before returning.
 */
class VoiceRecorder(
    private val sources: PcmSourceFactory,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val chunkBytes: Int = DEFAULT_CHUNK_BYTES,
    private val maxDurationMs: Long = 15_000,
) {
    suspend fun stream(session: VoiceSession, stop: AtomicBoolean): VoiceOutcome = withContext(io) {
        val source = try {
            sources.open()
        } catch (e: MicUnavailableException) {
            endQuietly(session)
            return@withContext when (e.reason) {
                MicUnavailableException.Reason.NO_PERMISSION -> VoiceOutcome.NO_PERMISSION
                MicUnavailableException.Reason.BUSY_OR_UNSUPPORTED -> VoiceOutcome.MIC_UNAVAILABLE
            }
        }

        val buffer = ByteArray(chunkBytes)
        var filled = 0
        var total = 0L
        val maxBytes = maxDurationMs * BYTES_PER_SECOND / 1_000
        var completed = false
        try {
            var micFailed = false
            while (!stop.get() && total < maxBytes) {
                currentCoroutineContext().ensureActive()
                val n = source.read(buffer, filled, minOf(READ_SLICE_BYTES, buffer.size - filled))
                if (n < 0) {
                    micFailed = true
                    break
                }
                filled += n
                total += n
                if (filled == buffer.size) {
                    session.send(buffer.copyOf())
                    filled = 0
                }
            }
            if (filled > 0) session.send(buffer.copyOf(filled))
            session.end()
            completed = true
            if (micFailed) VoiceOutcome.MIC_FAILED else VoiceOutcome.SENT
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            completed = true // the session is broken; ending it would fail the same way
            VoiceOutcome.SEND_FAILED
        } finally {
            source.release()
            buffer.fill(0)
            // Cancelled mid-utterance: the protocol has no "abort", so close the utterance
            // rather than leave the TV listening.
            if (!completed) withContext(NonCancellable) { endQuietly(session) }
        }
    }

    private suspend fun endQuietly(session: VoiceSession) {
        withTimeoutOrNull(END_TIMEOUT_MS) { runCatching { session.end() } }
    }

    companion object {
        private const val BYTES_PER_SECOND = VoiceFormat.SAMPLE_RATE_HZ * VoiceFormat.CHANNELS * VoiceFormat.BITS_PER_SAMPLE / 8L

        /** 100 ms of audio: bounds how long a stop request waits for a blocking read. */
        const val READ_SLICE_BYTES = 1_600

        /** 512 ms of audio, inside the 8–20 KiB range the Android TV protocol uses. */
        const val DEFAULT_CHUNK_BYTES = 8_192
        private const val END_TIMEOUT_MS = 1_000L
    }
}
