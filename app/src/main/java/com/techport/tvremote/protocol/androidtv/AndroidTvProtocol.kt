package com.techport.tvremote.protocol.androidtv

import android.content.Context
import android.os.Build
import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.DiscoveredDevice
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.RemoteException
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.protocol.androidtv.wire.DeviceInfo
import com.techport.tvremote.protocol.androidtv.wire.Feature
import com.techport.tvremote.protocol.core.PairingSession
import com.techport.tvremote.protocol.core.RemoteProtocol
import com.techport.tvremote.protocol.core.RemoteSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android TV / Google TV via the Android TV Remote Service v2 (see docs/PROTOCOLS.md).
 *
 * Pairing pins the TV certificate per device id; sessions only connect to that certificate and
 * present the app's Keystore-backed client certificate.
 */
class AndroidTvProtocol internal constructor(
    private val logger: AppLogger,
    private val identity: ClientIdentity,
    private val pins: CertificatePinStore,
    override val discovery: AndroidTvDiscovery?,
    private val clientName: String,
    private val deviceInfo: DeviceInfo,
    private val requestedFeatures: Int,
    private val io: CoroutineDispatcher,
    connectTimeoutMs: Int,
) : RemoteProtocol {

    /**
     * @param enableIme request keyboard support. Some TVs then show "use the keyboard on your phone"
     *   instead of their on-screen keyboard when a field is focused.
     * @param enableVoice request voice support (the TV then answers KEYCODE_SEARCH with a voice session).
     */
    constructor(
        context: Context,
        logger: AppLogger,
        enableIme: Boolean = true,
        enableVoice: Boolean = true,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    ) : this(
        logger = logger,
        identity = KeystoreClientIdentity(),
        pins = SharedPreferencesPinStore(context),
        discovery = AndroidTvDiscovery(context, logger),
        clientName = CLIENT_NAME,
        deviceInfo = DeviceInfo(
            model = Build.MODEL.orEmpty(),
            vendor = Build.MANUFACTURER.orEmpty(),
            unknown1 = 1,
            unknown2 = "1",
            packageName = "atvremote",
            appVersion = "1.0.0",
        ),
        requestedFeatures = AndroidTvSession.DEFAULT_FEATURES
            .let { if (enableIme) it else it and Feature.IME.inv() }
            .let { if (enableVoice) it else it and Feature.VOICE.inv() },
        io = Dispatchers.IO,
        connectTimeoutMs = connectTimeoutMs,
    )

    override val id: String = ID
    override val displayName: String = "Android TV / Google TV"
    override val usesLocalNetwork: Boolean = true

    private val connector = TlsConnector(identity, io, connectTimeoutMs)

    override suspend fun isPaired(device: TvDevice): Boolean =
        pins.get(device.id) != null && withContext(io) { identity.exists() }

    override fun createPairing(target: DiscoveredDevice): PairingSession = AndroidTvPairingSession(
        target = target,
        clientName = clientName,
        logger = logger,
        pins = pins,
        openChannel = {
            connector.connect(target.host, PAIRING_PORT, pinnedSha256 = null, readTimeoutMs = PAIRING_READ_TIMEOUT_MS)
        },
    )

    /**
     * Connects to the saved address; if nothing answers there, re-resolves the device id over mDNS
     * (≤ [RESOLVE_TIMEOUT_MS]) and tries the new address once. The returned [AndroidTvSession]
     * exposes the address it used, so callers can persist it.
     */
    override suspend fun openSession(device: TvDevice): RemoteSession {
        val pin = pins.get(device.id) ?: throw RemoteException(RemoteError.PAIRING_REJECTED, "Not paired")
        return try {
            open(device.host, device.port, pin)
        } catch (e: RemoteException) {
            if (e.error != RemoteError.UNREACHABLE && e.error != RemoteError.TIMEOUT) throw e
            val found = discovery?.resolve(device.id, RESOLVE_TIMEOUT_MS) ?: throw e
            if (found.host == device.host && found.port == device.port) throw e
            logger.i(TAG, "${device.name} moved; trying its new address")
            open(found.host, found.port, pin)
        }
    }

    override suspend fun forget(device: TvDevice) {
        // The client identity is shared by all TVs, so only this TV's pin goes.
        pins.remove(device.id)
    }

    /**
     * A target for a manually typed address. Throws [IllegalArgumentException] for an invalid host or
     * port; check [HostValidation.normalize] first to validate as the user types.
     */
    fun manualTarget(host: String, port: Int = REMOTE_PORT): DiscoveredDevice {
        val normalized = HostValidation.normalize(host) ?: throw IllegalArgumentException("Invalid host")
        require(HostValidation.isValidPort(port)) { "Invalid port" }
        return DiscoveredDevice(
            id = "atv:host:$normalized",
            name = normalized,
            host = normalized,
            port = port,
            protocolId = ID,
        )
    }

    private suspend fun open(host: String, port: Int, pin: String): AndroidTvSession {
        val channel = connector.connect(host, port, pin, readTimeoutMs = SESSION_READ_TIMEOUT_MS)
        val session = AndroidTvSession(
            transport = channel.transport,
            logger = logger,
            connectedHost = host,
            connectedPort = port,
            requestedFeatures = requestedFeatures,
            deviceInfo = deviceInfo,
            dispatcher = io,
        )
        try {
            session.start()
            session.awaitReady()
            return session
        } catch (e: Throwable) {
            session.close()
            if (e is CancellationException || e is RemoteException) throw e
            throw RemoteException(mapIoError(e, established = false), cause = e)
        }
    }

    companion object {
        /** Persisted in [TvDevice.protocolId]; never change. */
        const val ID = "androidtv"
        const val REMOTE_PORT = 6466
        const val PAIRING_PORT = 6467
        const val DEFAULT_CONNECT_TIMEOUT_MS = 3_000
        const val RESOLVE_TIMEOUT_MS = 3_000L

        /** Shown on the TV while pairing. */
        const val CLIENT_NAME = "TV Remote"
        private const val TAG = "AndroidTv"
        private const val PAIRING_READ_TIMEOUT_MS = 10_000

        // A safety net only; the session's idle watchdog (16 s) normally fires first.
        private const val SESSION_READ_TIMEOUT_MS = 30_000
    }
}
