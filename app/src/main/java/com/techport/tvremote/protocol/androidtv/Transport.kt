package com.techport.tvremote.protocol.androidtv

import com.techport.tvremote.protocol.androidtv.wire.FrameReader
import com.techport.tvremote.protocol.androidtv.wire.Framing
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.Socket
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * A message-oriented connection to the TV. Framing lives below this interface so pairing and
 * session logic can be tested against an in-memory implementation.
 */
interface MessageTransport : Closeable {
    /** Next whole message. Throws [java.io.EOFException] once the peer or [close] ended the stream. */
    suspend fun receive(): ByteArray

    suspend fun send(message: ByteArray)

    /** Idempotent; safe from any thread, including main. Unblocks a pending [receive]. */
    override fun close()
}

/** An authenticated connection plus the public keys pairing mixes into its secret. */
class SecureChannel(
    val transport: MessageTransport,
    val clientKey: RSAPublicKey,
    val serverKey: RSAPublicKey,
    /** SHA-256 of the TV certificate (lowercase hex); what gets pinned. */
    val serverFingerprint: String,
)

/** Length-prefixed frames over a (TLS) socket. Blocking I/O runs on [io]. */
internal class SocketTransport(private val socket: Socket, private val io: CoroutineDispatcher) : MessageTransport {
    private val reader = FrameReader(BufferedInputStream(socket.getInputStream()))
    private val output = socket.getOutputStream()
    private val writeLock = Any()
    private val closed = AtomicBoolean(false)

    override suspend fun receive(): ByteArray = withContext(io) { reader.readFrame() }

    override suspend fun send(message: ByteArray) = withContext(io) {
        val frame = Framing.frame(message)
        // One write per frame so a message is not split across TLS records needlessly.
        synchronized(writeLock) {
            output.write(frame)
            output.flush()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // SSLSocket.close() may write close_notify, which is network I/O: never do it on the caller's thread.
        thread(name = "atv-socket-close", isDaemon = true) { runCatching { socket.close() } }
    }
}
