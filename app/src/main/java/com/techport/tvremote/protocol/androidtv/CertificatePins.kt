package com.techport.tvremote.protocol.androidtv

import android.content.Context
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TV certificate fingerprints pinned at pairing (trust on first use), keyed by device id.
 * Fingerprints are public data, so they are not encrypted; backup is disabled app-wide.
 */
interface CertificatePinStore {
    suspend fun get(deviceId: String): String?
    suspend fun put(deviceId: String, sha256Hex: String)
    suspend fun remove(deviceId: String)
}

class SharedPreferencesPinStore(context: Context) : CertificatePinStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun get(deviceId: String): String? = withContext(Dispatchers.IO) { prefs.getString(deviceId, null) }

    override suspend fun put(deviceId: String, sha256Hex: String) {
        withContext(Dispatchers.IO) { prefs.edit().putString(deviceId, sha256Hex).commit() }
    }

    override suspend fun remove(deviceId: String) {
        withContext(Dispatchers.IO) { prefs.edit().remove(deviceId).commit() }
    }

    private companion object {
        const val PREFS_NAME = "androidtv_cert_pins"
    }
}

internal object Fingerprints {
    fun sha256Hex(cert: X509Certificate): String = sha256Hex(cert.encoded)

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** The TV presented a certificate other than the pinned one. */
internal class CertificatePinMismatchException : CertificateException("Server certificate does not match the pinned fingerprint")

/**
 * Trusts exactly the pinned certificate, or (while pairing, [expectedSha256] == null) any certificate,
 * which it records so it can be pinned once the user confirms the code. TVs use self-signed
 * certificates, so CA validation is meaningless here; the pin is the trust decision.
 */
internal class PinningTrustManager(private val expectedSha256: String?) : X509TrustManager {
    @Volatile
    var serverCertificate: X509Certificate? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("TV sent no certificate")
        if (expectedSha256 != null && !Fingerprints.sha256Hex(leaf).equals(expectedSha256, ignoreCase = true)) {
            throw CertificatePinMismatchException()
        }
        serverCertificate = leaf
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("Client-side trust manager only")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
