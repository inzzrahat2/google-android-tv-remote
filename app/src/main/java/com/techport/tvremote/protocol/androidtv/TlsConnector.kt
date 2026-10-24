package com.techport.tvremote.protocol.androidtv

import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.RemoteException
import com.techport.tvremote.protocol.androidtv.wire.ProtoException
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Opens TLS connections with our client certificate and, when given, a pinned TV certificate. */
internal class TlsConnector(
    private val identity: ClientIdentity,
    private val io: CoroutineDispatcher,
    private val connectTimeoutMs: Int,
    private val handshakeTimeoutMs: Int = 6_000,
) {
    /**
     * Connects to [host]:[port]. [pinnedSha256] null means "pairing": accept and report whatever the
     * TV presents. [readTimeoutMs] bounds every later blocking read (0 = none).
     * Throws [RemoteException] (already mapped) on failure; never leaks the socket.
     */
    suspend fun connect(host: String, port: Int, pinnedSha256: String?, readTimeoutMs: Int): SecureChannel =
        withContext(io) {
            val credentials = try {
                identity.obtain()
            } catch (e: Exception) {
                throw RemoteException(RemoteError.UNKNOWN, "Client identity unavailable", e)
            }
            val trust = PinningTrustManager(pinnedSha256)
            val context = SSLContext.getInstance("TLS").apply { init(credentials.keyManagers, arrayOf(trust), null) }
            val raw = Socket()
            var tls: SSLSocket? = null
            try {
                raw.tcpNoDelay = true
                raw.connect(InetSocketAddress(host, port), connectTimeoutMs)
                raw.soTimeout = handshakeTimeoutMs
                tls = (context.socketFactory.createSocket(raw, host, port, true) as SSLSocket).apply {
                    useClientMode = true
                    startHandshake()
                }
                val serverCert = trust.serverCertificate ?: throw SSLException("TV certificate was not checked")
                // Belt and braces: the trust manager already enforced the pin during the handshake.
                val fingerprint = Fingerprints.sha256Hex(serverCert)
                if (pinnedSha256 != null && !fingerprint.equals(pinnedSha256, ignoreCase = true)) {
                    throw CertificatePinMismatchException()
                }
                val serverKey = serverCert.publicKey as? RSAPublicKey
                    ?: throw ProtoException("TV certificate key is not RSA")
                tls.soTimeout = readTimeoutMs
                SecureChannel(SocketTransport(tls, io), credentials.publicKey, serverKey, fingerprint)
            } catch (e: Throwable) {
                runCatching { tls?.close() }
                runCatching { raw.close() }
                if (e is RemoteException) throw e
                val mapped = mapIoError(e, established = false)
                throw RemoteException(mapped, "TLS connect to port $port failed: ${e.javaClass.simpleName}", e)
            }
        }
}

/**
 * Maps transport failures to [RemoteError]. [established] = the session had reached "ready",
 * after which any drop is a lost connection rather than a credential problem.
 */
internal fun mapIoError(e: Throwable, established: Boolean): RemoteError {
    if (e is RemoteException) return e.error
    if (e.causes().any { it is CertificatePinMismatchException }) return RemoteError.CERTIFICATE_CHANGED
    return when (e) {
        is ProtoException -> RemoteError.PROTOCOL_ERROR
        is SocketTimeoutException -> if (established) RemoteError.CONNECTION_LOST else RemoteError.TIMEOUT
        is ConnectException, is NoRouteToHostException, is UnknownHostException, is PortUnreachableException ->
            RemoteError.UNREACHABLE
        // Before the session is up, a TLS failure means the TV refused our certificate (it no longer
        // trusts this phone, e.g. after "clear data" on the TV). Afterwards it is just a dropped link.
        is SSLException -> if (established) RemoteError.CONNECTION_LOST else RemoteError.PAIRING_REJECTED
        is EOFException, is SocketException -> RemoteError.CONNECTION_LOST
        is IOException -> RemoteError.CONNECTION_LOST
        else -> RemoteError.UNKNOWN
    }
}

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause.takeIf { c -> c !== it } }
