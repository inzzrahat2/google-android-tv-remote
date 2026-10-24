package com.techport.tvremote.protocol.androidtv

import com.techport.tvremote.protocol.androidtv.wire.RemoteMessage
import java.io.EOFException
import java.io.IOException
import java.math.BigInteger
import java.security.interfaces.RSAPublicKey
import kotlinx.coroutines.channels.Channel

/** In-memory [MessageTransport]: the test plays the TV on the other end. */
internal class FakeTransport : MessageTransport {
    /** Messages the client sent. Stays readable after [close]. */
    val toTv = Channel<ByteArray>(Channel.UNLIMITED)

    /** Messages the TV sends; closing it is an EOF for the client. */
    val fromTv = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile
    var closed = false
        private set

    override suspend fun receive(): ByteArray = fromTv.receiveCatching().getOrNull() ?: throw EOFException("closed")

    override suspend fun send(message: ByteArray) {
        if (closed) throw IOException("closed")
        toTv.send(message)
    }

    override fun close() {
        closed = true
        fromTv.close()
    }

    suspend fun push(message: RemoteMessage) = fromTv.send(message.encode())

    suspend fun next(): RemoteMessage = RemoteMessage.decode(toTv.receive())

    fun nothingSent(): Boolean = toTv.tryReceive().isFailure
}

internal class InMemoryPinStore : CertificatePinStore {
    val pins = mutableMapOf<String, String>()
    override suspend fun get(deviceId: String) = pins[deviceId]
    override suspend fun put(deviceId: String, sha256Hex: String) {
        pins[deviceId] = sha256Hex
    }

    override suspend fun remove(deviceId: String) {
        pins.remove(deviceId)
    }
}

/** An RSA public key with arbitrary numbers (lets tests use odd-length hex values). */
internal class FakeRsaKey(private val n: BigInteger, private val e: BigInteger) : RSAPublicKey {
    override fun getModulus(): BigInteger = n
    override fun getPublicExponent(): BigInteger = e
    override fun getAlgorithm() = "RSA"
    override fun getFormat(): String? = null
    override fun getEncoded(): ByteArray? = null
}

internal fun hex(vararg bytes: Int): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }
