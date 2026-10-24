package com.techport.tvremote.protocol.androidtv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

/** The certificate and key this app presents to TVs (TLS client authentication). */
class ClientCredentials(
    val keyManagers: Array<KeyManager>,
    val certificate: X509Certificate,
) {
    val publicKey: RSAPublicKey get() = certificate.publicKey as RSAPublicKey
}

/** Source of the app's TV-facing identity. Blocking: call off the main thread. */
interface ClientIdentity {
    /** True if credentials exist already (so a TV may have paired with them). */
    fun exists(): Boolean

    /** Loads the credentials, creating them on first use. */
    fun obtain(): ClientCredentials
}

/**
 * One RSA-2048 identity for the whole app, generated inside the Android Keystore. The private key
 * is non-exportable; only its self-signed certificate ever leaves the phone (in the TLS handshake).
 *
 * TVs pin this certificate when pairing, so deleting the key un-pairs every TV at once.
 */
class KeystoreClientIdentity(private val alias: String = DEFAULT_ALIAS) : ClientIdentity {
    @Volatile
    private var cached: ClientCredentials? = null

    override fun exists(): Boolean = runCatching { keyStore().containsAlias(alias) }.getOrDefault(false)

    @Synchronized
    override fun obtain(): ClientCredentials {
        cached?.let { return it }
        val store = keyStore()
        if (!store.containsAlias(alias)) generate()
        val key = store.getKey(alias, null) as? PrivateKey ?: error("Keystore entry $alias has no private key")
        val cert = store.getCertificate(alias) as? X509Certificate ?: error("Keystore entry $alias has no certificate")
        return ClientCredentials(arrayOf(FixedClientKeyManager(alias, key, arrayOf(cert))), cert).also { cached = it }
    }

    private fun generate() {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            // NONE + PKCS1 for TLS 1.2 client signatures; SHA-2 + PSS for TLS 1.3.
            .setDigests(
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512,
            )
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            .setCertificateSubject(X500Principal("CN=atvremote"))
            .setCertificateSerialNumber(BigInteger(63, SecureRandom()).add(BigInteger.ONE))
            .setCertificateNotBefore(Date(now - TimeUnit.DAYS.toMillis(1)))
            .setCertificateNotAfter(Date(now + TimeUnit.DAYS.toMillis(365L * 25)))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).apply {
            initialize(spec)
            generateKeyPair()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val DEFAULT_ALIAS = "atv_remote_client"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/**
 * Always presents our one certificate. A KeyManagerFactory-built manager filters by the issuer list
 * in the server's CertificateRequest and would send no certificate if the TV lists only its own CA;
 * the reference client (Python ssl.load_cert_chain) always sends its certificate, so we do too.
 */
internal class FixedClientKeyManager(
    private val alias: String,
    private val key: PrivateKey,
    private val chain: Array<X509Certificate>,
) : X509ExtendedKeyManager() {
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = alias
    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = alias
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(alias)
    override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if (alias == this.alias) chain.clone() else null
    override fun getPrivateKey(alias: String?): PrivateKey? = if (alias == this.alias) key else null
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
}
