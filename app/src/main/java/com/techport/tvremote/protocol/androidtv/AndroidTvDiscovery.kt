package com.techport.tvremote.protocol.androidtv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.DiscoveredDevice
import com.techport.tvremote.protocol.core.DiscoverySource
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.Executor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds `_androidtvremote2._tcp` services with [NsdManager].
 *
 * **Device id.** It must survive DHCP address changes, so it never uses the IP. The TXT record
 * `bt` (the TV's Bluetooth MAC, published by the Android TV Remote Service) is preferred:
 * it is unique and survives renaming the TV. Without it the mDNS service name is used, which is
 * stable unless the user renames the TV (then the TV must be paired again).
 * Format: `atv:bt:<mac>` or `atv:name:<service name>`; manual entries use `atv:host:<host>`.
 */
class AndroidTvDiscovery(context: Context, private val logger: AppLogger) : DiscoverySource {
    private val appContext = context.applicationContext

    override fun discover(): Flow<List<DiscoveredDevice>> = callbackFlow {
        val nsd = appContext.getSystemService(NsdManager::class.java)
        if (nsd == null) {
            logger.w(TAG, "NsdManager unavailable; discovery disabled")
            send(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val lock = Any()
        val found = LinkedHashMap<String, DiscoveredDevice>() // by service name
        var stopped = false

        fun publish() {
            trySend(synchronized(lock) { found.values.toList() })
        }

        fun onResolved(info: NsdServiceInfo) {
            val device = toDevice(info) ?: return
            synchronized(lock) {
                if (stopped) return
                found[info.serviceName] = device
            }
            publish()
        }

        fun onGone(serviceName: String) {
            val removed = synchronized(lock) { found.remove(serviceName) != null }
            if (removed) publish()
        }

        val resolver: Resolver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            CallbackResolver(nsd, ::onResolved, ::onGone)
        } else {
            SerialResolver(nsd, ::onResolved)
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = logger.d(TAG, "Discovery started")
            override fun onDiscoveryStopped(serviceType: String) = logger.d(TAG, "Discovery stopped")

            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.contains(SERVICE_NAME_FRAGMENT, ignoreCase = true)) return
                resolver.add(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                resolver.remove(info.serviceName)
                onGone(info.serviceName)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.w(TAG, "Discovery failed to start (code $errorCode)")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.w(TAG, "Discovery failed to stop (code $errorCode)")
            }
        }

        send(emptyList())
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: RuntimeException) {
            // Missing local-network permission on Android 17+, or NSD unavailable.
            logger.w(TAG, "Could not start discovery", e)
        }

        awaitClose {
            synchronized(lock) { stopped = true }
            resolver.stop()
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    /** Looks for [deviceId] on the network for at most [timeoutMs]; null if not seen. */
    suspend fun resolve(deviceId: String, timeoutMs: Long): DiscoveredDevice? = withTimeoutOrNull(timeoutMs) {
        discover().mapNotNull { list -> list.firstOrNull { it.id == deviceId } }.first()
    }

    private fun toDevice(info: NsdServiceInfo): DiscoveredDevice? {
        val address = addressOf(info) ?: return null
        val port = info.port.takeIf { it in 1..65535 } ?: AndroidTvProtocol.REMOTE_PORT
        val mac = info.attributes?.get("bt")?.let { String(it, Charsets.UTF_8) }
        return DiscoveredDevice(
            id = deviceIdFor(info.serviceName, mac),
            name = info.serviceName,
            host = address.hostAddress ?: return null,
            port = port,
            protocolId = AndroidTvProtocol.ID,
        )
    }

    @Suppress("DEPRECATION")
    private fun addressOf(info: NsdServiceInfo): InetAddress? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.let { all -> all.firstOrNull { it is Inet4Address } ?: all.firstOrNull() }
        } else {
            info.host
        }

    private interface Resolver {
        fun add(info: NsdServiceInfo)
        fun remove(serviceName: String)
        fun stop()
    }

    /** Pre-API 34: NsdManager allows one resolve at a time, so resolves are queued. */
    @Suppress("DEPRECATION")
    private inner class SerialResolver(
        private val nsd: NsdManager,
        private val onResolved: (NsdServiceInfo) -> Unit,
    ) : Resolver {
        private val queue = ArrayDeque<NsdServiceInfo>()
        private var inFlight = false
        private var stopped = false

        override fun add(info: NsdServiceInfo) {
            synchronized(this) {
                if (queue.none { it.serviceName == info.serviceName }) queue.addLast(info)
                next()
            }
        }

        override fun remove(serviceName: String) {
            synchronized(this) { queue.removeAll { it.serviceName == serviceName } }
        }

        override fun stop() {
            synchronized(this) {
                stopped = true
                queue.clear()
            }
        }

        private fun next() {
            if (inFlight || stopped) return
            val info = queue.removeFirstOrNull() ?: return
            inFlight = true
            try {
                nsd.resolveService(info, listener)
            } catch (e: RuntimeException) {
                logger.w(TAG, "Resolve failed to start", e)
                inFlight = false
            }
        }

        private val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                onResolved(info)
                done()
            }

            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                logger.d(TAG, "Resolve failed (code $errorCode)")
                done()
            }
        }

        private fun done() = synchronized(this) {
            inFlight = false
            next()
        }
    }

    /** API 34+: one ServiceInfoCallback per service tracks address changes and loss. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private inner class CallbackResolver(
        private val nsd: NsdManager,
        private val onResolved: (NsdServiceInfo) -> Unit,
        private val onGone: (String) -> Unit,
    ) : Resolver {
        private val callbacks = HashMap<String, NsdManager.ServiceInfoCallback>()
        private var stopped = false
        private val direct = Executor { it.run() }

        override fun add(info: NsdServiceInfo) {
            synchronized(this) { if (stopped || info.serviceName in callbacks) return }
            val name = info.serviceName
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    logger.d(TAG, "Service callback registration failed (code $errorCode)")
                    synchronized(this@CallbackResolver) { callbacks.remove(name) }
                }

                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) = onResolved(serviceInfo)
                override fun onServiceLost() = onGone(name)
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            try {
                // Recorded first: the direct executor may deliver a failure callback synchronously.
                synchronized(this) { callbacks[name] = callback }
                nsd.registerServiceInfoCallback(info, direct, callback)
            } catch (e: RuntimeException) {
                synchronized(this) { callbacks.remove(name) }
                logger.w(TAG, "Could not watch a service", e)
            }
        }

        override fun remove(serviceName: String) {
            val callback = synchronized(this) { callbacks.remove(serviceName) } ?: return
            runCatching { nsd.unregisterServiceInfoCallback(callback) }
        }

        override fun stop() {
            val all = synchronized(this) {
                stopped = true
                callbacks.values.toList().also { callbacks.clear() }
            }
            all.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
    }

    companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp"
        private const val SERVICE_NAME_FRAGMENT = "_androidtvremote2"
        private const val TAG = "AtvDiscovery"

        /** Stable id: Bluetooth MAC from TXT `bt` when it looks like a MAC, else the service name. */
        fun deviceIdFor(serviceName: String, btMac: String?): String {
            val mac = btMac?.trim()?.lowercase(Locale.ROOT)
            return if (mac != null && MAC.matches(mac) && mac != "00:00:00:00:00:00") "atv:bt:$mac" else "atv:name:$serviceName"
        }

        private val MAC = Regex("^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")
    }
}
