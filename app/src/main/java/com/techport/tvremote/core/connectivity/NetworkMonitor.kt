package com.techport.tvremote.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Local network the TV could be on. [networkId] changes on Wi-Fi to Wi-Fi switches. */
data class NetworkStatus(val isLocalNetworkAvailable: Boolean, val networkId: String?) {
    companion object {
        val NONE = NetworkStatus(false, null)
    }
}

class NetworkMonitor(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val status: Flow<NetworkStatus> = callbackFlow {
        fun statusOf(network: Network?, caps: NetworkCapabilities?): NetworkStatus {
            val local = caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
            return if (local) NetworkStatus(true, network.toString()) else NetworkStatus.NONE
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(statusOf(network, caps))
            }

            override fun onLost(network: Network) {
                trySend(NetworkStatus.NONE)
            }
        }
        val active = connectivity.activeNetwork
        trySend(statusOf(active, connectivity.getNetworkCapabilities(active)))
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
