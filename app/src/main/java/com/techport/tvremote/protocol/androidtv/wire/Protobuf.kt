package com.techport.tvremote.protocol.androidtv.wire

import java.io.ByteArrayOutputStream
import java.io.IOException

/** Malformed protobuf or framing from the peer. Maps to PROTOCOL_ERROR. */
class ProtoException(message: String) : IOException(message)

internal object WireType {
    const val VARINT = 0
    const val FIXED64 = 1
    const val LENGTH_DELIMITED = 2
    const val FIXED32 = 5
}

/**
 * Minimal protobuf encoder: just the wire features the Android TV messages use
 * (varint ints/bools/enums, strings, bytes and nested messages). No zigzag types.
 */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    /** int32/uint32/enum. Negative int32 values are sign-extended to 10 bytes, as protobuf does. */
    fun int(field: Int, value: Int) {
        tag(field, WireType.VARINT)
        writeVarint(out, value.toLong())
    }

    fun bool(field: Int, value: Boolean) = int(field, if (value) 1 else 0)

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun bytes(field: Int, value: ByteArray) {
        tag(field, WireType.LENGTH_DELIMITED)
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    fun message(field: Int, body: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(body).toByteArray())

    // proto3 omits default scalar values; these helpers keep encoders terse.
    fun intIfSet(field: Int, value: Int) { if (value != 0) int(field, value) }
    fun boolIfSet(field: Int, value: Boolean) { if (value) bool(field, true) }
    fun stringIfSet(field: Int, value: String) { if (value.isNotEmpty()) string(field, value) }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())

    companion object {
        fun writeVarint(out: ByteArrayOutputStream, value: Long) {
            var v = value
            while (v and 0x7FL.inv() != 0L) {
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            out.write(v.toInt())
        }

        fun encode(body: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(body).toByteArray()
    }
}

/**
 * Minimal protobuf decoder over a byte range. Call [next] until it returns false; read the current
 * field with the matching typed reader, or [skip] it. Unknown fields must be skipped.
 */
internal class ProtoReader(private val buf: ByteArray, private var pos: Int = 0, private val end: Int = buf.size) {
    var field: Int = 0
        private set
    var wireType: Int = -1
        private set

    fun next(): Boolean {
        if (pos >= end) return false
        val key = readRawVarint()
        if (key ushr 32 != 0L) throw ProtoException("Bad field key")
        field = (key ushr 3).toInt()
        wireType = (key and 7).toInt()
        if (field <= 0) throw ProtoException("Bad field number $field")
        return true
    }

    fun int(): Int {
        expect(WireType.VARINT)
        return readRawVarint().toInt()
    }

    fun bool(): Boolean = int() != 0

    fun bytes(): ByteArray {
        val (start, length) = lengthDelimited()
        return buf.copyOfRange(start, start + length)
    }

    fun string(): String {
        val (start, length) = lengthDelimited()
        return String(buf, start, length, Charsets.UTF_8)
    }

    /** Reader positioned over the current nested message. */
    fun message(): ProtoReader {
        val (start, length) = lengthDelimited()
        return ProtoReader(buf, start, start + length)
    }

    fun skip() {
        when (wireType) {
            WireType.VARINT -> readRawVarint()
            WireType.FIXED64 -> advance(8)
            WireType.LENGTH_DELIMITED -> lengthDelimited()
            WireType.FIXED32 -> advance(4)
            else -> throw ProtoException("Unsupported wire type $wireType")
        }
    }

    private fun lengthDelimited(): Pair<Int, Int> {
        expect(WireType.LENGTH_DELIMITED)
        val length = readRawVarint()
        if (length < 0 || length > end - pos) throw ProtoException("Truncated field")
        val start = pos
        pos += length.toInt()
        return start to length.toInt()
    }

    private fun advance(n: Int) {
        if (end - pos < n) throw ProtoException("Truncated field")
        pos += n
    }

    private fun expect(type: Int) {
        if (wireType != type) throw ProtoException("Field $field: wire type $wireType, expected $type")
    }

    private fun readRawVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            if (pos >= end) throw ProtoException("Truncated varint")
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw ProtoException("Varint too long")
    }
}

/** Visits every field in order; [onField] must read or [ProtoReader.skip] the current field. */
internal inline fun ProtoReader.forEachField(onField: ProtoReader.(field: Int) -> Unit) {
    while (next()) onField(field)
}
