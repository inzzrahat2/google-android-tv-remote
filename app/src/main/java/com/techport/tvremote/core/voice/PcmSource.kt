package com.techport.tvremote.core.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.techport.tvremote.domain.model.VoiceFormat

/** A started microphone producing PCM in [VoiceFormat]. Abstracted so recording logic is testable. */
interface PcmSource {
    /** Blocking read into [buffer]. Returns bytes read (0 is allowed) or a negative error code. */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    /** Stops and frees the microphone. Safe to call more than once. */
    fun release()
}

/** Opens and starts a [PcmSource]; throws [MicUnavailableException] if the mic can't be used. */
fun interface PcmSourceFactory {
    fun open(): PcmSource
}

class MicUnavailableException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NO_PERMISSION, BUSY_OR_UNSUPPORTED }
}

/**
 * [AudioRecord] at [VoiceFormat] (8 kHz, mono, PCM 16-bit). Prefers the VOICE_RECOGNITION source,
 * which is tuned for speech-to-text (no AGC/noise processing that hurts recognition), and falls
 * back to MIC on devices that don't offer it.
 */
class AudioRecordPcmSourceFactory(context: Context) : PcmSourceFactory {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission") // Checked just below; AudioRecord also throws SecurityException.
    override fun open(): PcmSource {
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw MicUnavailableException(MicUnavailableException.Reason.NO_PERMISSION, "RECORD_AUDIO not granted")
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) {
            throw MicUnavailableException(MicUnavailableException.Reason.BUSY_OR_UNSUPPORTED, "8 kHz capture unsupported")
        }
        val bufferSize = maxOf(minBuffer * 2, MIN_BUFFER_BYTES)
        for (source in SOURCES) {
            val record = try {
                AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
            } catch (e: SecurityException) {
                throw MicUnavailableException(MicUnavailableException.Reason.NO_PERMISSION, "Permission revoked")
            } catch (e: IllegalArgumentException) {
                continue
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                continue
            }
            try {
                record.startRecording()
            } catch (e: IllegalStateException) {
                record.release()
                continue
            }
            // Another app (a call, another recorder) can hold the mic; we'd only read silence.
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.release()
                continue
            }
            return AudioRecordPcmSource(record)
        }
        throw MicUnavailableException(MicUnavailableException.Reason.BUSY_OR_UNSUPPORTED, "No usable audio source")
    }

    private companion object {
        const val SAMPLE_RATE = VoiceFormat.SAMPLE_RATE_HZ
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val MIN_BUFFER_BYTES = 3_200 // 200 ms
        val SOURCES = intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
    }
}

private class AudioRecordPcmSource(private val record: AudioRecord) : PcmSource {
    @Volatile
    private var released = false

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        if (released) -1 else record.read(buffer, offset, length)

    @Synchronized
    override fun release() {
        if (released) return
        released = true
        runCatching { record.stop() }
        record.release()
    }
}
