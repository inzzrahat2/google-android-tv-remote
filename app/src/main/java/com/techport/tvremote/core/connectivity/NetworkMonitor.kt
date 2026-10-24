package com.techport.tvremote.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Local network the TV could be on.
 *
 * [networkId] changes on Wi-Fi to Wi-Fi switches (and when the same SSID is rejoined after a
 * router restart, because Android allocates a new [Network]). [subnets] are the phone's IPv4
 * subnets on that network; empty when unknown.
 */
data class NetworkStatus(
    val isLocalNetworkAvailable: Boolean,
    val networkId: String?,
    val subnets: List<Ipv4Subnet> = emptyList(),
) {
    companion object {
        val NONE = NetworkStatus(false, null)
    }
}

/**
 * Cold flow of the phone's default network, reduced to "is there a LAN the TV could be on".
 *
 * Wi-Fi and Ethernet count; mobile data, airplane mode and no network do not. Collecting it
 * registers a default-network callback; cancelling unregisters it.
 */
class NetworkMonitor(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val status: Flow<NetworkStatus> = callbackFlow {
        // Only touched from the callback thread (and once before registering).
        var current: Network? = null
        var caps: NetworkCapabilities? = null
        var links: LinkProperties? = null

        fun emit() {
            trySend(statusOf(current, caps, links))
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (network != current) links = null
                current = network
                caps = networkCapabilities
                emit()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (network != current) return
                links = linkProperties
                emit()
            }

            override fun onLost(network: Network) {
                // On a Wi-Fi to Wi-Fi switch the new default may already be reported; ignore the old one.
                if (network != current) return
                current = null
                caps = null
                links = null
                emit()
            }
        }
        current = connectivity.activeNetwork
        caps = current?.let(connectivity::getNetworkCapabilities)
        links = current?.let(connectivity::getLinkProperties)
        emit()
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun statusOf(network: Network?, caps: NetworkCapabilities?, links: LinkProperties?): NetworkStatus {
        val local = network != null && caps != null && (
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
        if (!local) return NetworkStatus.NONE
        val subnets = links?.linkAddresses.orEmpty().mapNotNull { link ->
            (link.address as? Inet4Address)?.let { Ipv4Subnet.of(it.address, link.prefixLength) }
        }
        return NetworkStatus(true, network.toString(), subnets)
    }
}
