package com.techport.tvremote.protocol.androidtv.wire

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

/** Both Android TV ports prefix every protobuf message with its length as a varint. */
internal object Framing {
    /** Far above anything the TV sends (voice payloads are ≤20 KiB); guards against garbage lengths. */
    const val MAX_FRAME_BYTES = 512 * 1024

    fun frame(message: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(message.size + 5)
        ProtoWriter.writeVarint(out, message.size.toLong())
        out.write(message)
        return out.toByteArray()
    }
}

/**
 * Blocking reader of length-prefixed frames. Tolerates any split of the byte stream, including
 * inside the length varint. A clean EOF between frames and a truncated frame both throw [EOFException].
 */
internal class FrameReader(private val input: InputStream, private val maxFrameBytes: Int = Framing.MAX_FRAME_BYTES) {

    fun readFrame(): ByteArray {
        val length = readLength()
        if (length > maxFrameBytes) throw ProtoException("Frame of $length bytes exceeds limit")
        val frame = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(frame, read, length - read)
            if (n < 0) throw EOFException("Stream ended inside a frame")
            read += n
        }
        return frame
    }

    private fun readLength(): Int {
        var result = 0L
        var shift = 0
        while (shift < 35) {
            val b = input.read()
            if (b < 0) throw EOFException(if (shift == 0) "Stream closed" else "Stream ended inside a length")
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) {
                if (result > Int.MAX_VALUE) throw ProtoException("Frame length too large")
                return result.toInt()
            }
            shift += 7
        }
        throw ProtoException("Frame length varint too long")
    }
}
