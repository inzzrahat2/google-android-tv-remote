package com.techport.tvremote.core.connectivity

import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.TvDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionIssuesTest {
    private val tv = TvDevice("tv1", "Living Room", "192.168.1.20", 6466, "androidtv")
    private val home = Ipv4Subnet.of(byteArrayOf(192.toByte(), 168.toByte(), 1, 5), 24)!!
    private val wifi = NetworkStatus(true, "net-1", listOf(home))
    private val none = NetworkStatus.NONE

    @Test
    fun noNetworkOverridesErrorsWhileNotConnected() {
        assertEquals(RemoteError.NO_NETWORK, ConnectionIssues.uiError(ConnectionState.Connecting(tv), none))
        assertEquals(
            RemoteError.NO_NETWORK,
            ConnectionIssues.uiError(ConnectionState.Reconnecting(tv, 2, RemoteError.TIMEOUT), none),
        )
        assertEquals(
            RemoteError.NO_NETWORK,
            ConnectionIssues.uiError(ConnectionState.Disconnected(tv, RemoteError.UNREACHABLE), none),
        )
    }

    @Test
    fun stateErrorShownWhenNetworkIsFine() {
        assertEquals(
            RemoteError.TIMEOUT,
            ConnectionIssues.uiError(ConnectionState.Reconnecting(tv, 2, RemoteError.TIMEOUT), wifi),
        )
        assertNull(ConnectionIssues.uiError(ConnectionState.Connecting(tv), wifi))
    }

    @Test
    fun permissionComesAfterNetworkButBeforeStateError() {
        val state = ConnectionState.Disconnected(tv, RemoteError.TIMEOUT)
        assertEquals(RemoteError.LOCAL_NETWORK_PERMISSION, ConnectionIssues.uiError(state, wifi, localNetworkPermissionGranted = false))
        assertEquals(RemoteError.NO_NETWORK, ConnectionIssues.uiError(state, none, localNetworkPermissionGranted = false))
    }

    @Test
    fun connectedIdleUserDisconnectAndPairingAreLeftAlone() {
        assertNull(ConnectionIssues.uiError(ConnectionState.Connected(tv, RemoteCapabilities.NONE), none))
        assertNull(ConnectionIssues.uiError(ConnectionState.Idle, none))
        assertNull(ConnectionIssues.uiError(ConnectionState.Disconnected(tv, null), none))
        assertEquals(
            RemoteError.CERTIFICATE_CHANGED,
            ConnectionIssues.uiError(ConnectionState.PairingRequired(tv, RemoteError.CERTIFICATE_CHANGED), none),
        )
    }

    @Test
    fun differentSubnetDetectedOnlyForReachabilityFailures() {
        val guest = NetworkStatus(true, "net-2", listOf(Ipv4Subnet.of(byteArrayOf(10, 0, 0, 7), 24)!!))
        val failing = ConnectionState.Disconnected(tv, RemoteError.UNREACHABLE)
        assertTrue(ConnectionIssues.likelyDifferentNetwork(failing, guest))
        assertFalse(ConnectionIssues.likelyDifferentNetwork(failing, wifi))
        assertFalse(ConnectionIssues.likelyDifferentNetwork(ConnectionState.Disconnected(tv, RemoteError.PAIRING_REJECTED), guest))
        assertFalse("no subnet info", ConnectionIssues.likelyDifferentNetwork(failing, NetworkStatus(true, "net-3")))
        assertFalse("no network", ConnectionIssues.likelyDifferentNetwork(failing, none))
        val named = ConnectionState.Reconnecting(tv.copy(host = "tv.local"), 1, RemoteError.TIMEOUT)
        assertFalse("host names are unknown", ConnectionIssues.likelyDifferentNetwork(named, guest))
    }

    @Test
    fun retryOnNetworkAppearingOrChanging() {
        assertTrue(ConnectionIssues.shouldRetryAfterNetworkChange(null, wifi))
        assertTrue(ConnectionIssues.shouldRetryAfterNetworkChange(none, wifi))
        assertTrue(ConnectionIssues.shouldRetryAfterNetworkChange(wifi, wifi.copy(networkId = "net-2")))
        assertFalse(ConnectionIssues.shouldRetryAfterNetworkChange(wifi, none))
        assertFalse("same network, new address info", ConnectionIssues.shouldRetryAfterNetworkChange(wifi, wifi.copy(subnets = emptyList())))
    }

    @Test
    fun recoverableErrors() {
        assertTrue(ConnectionIssues.isRecoverable(RemoteError.TIMEOUT))
        assertTrue(ConnectionIssues.isRecoverable(RemoteError.NO_NETWORK))
        assertFalse(ConnectionIssues.isRecoverable(null))
        assertFalse(ConnectionIssues.isRecoverable(RemoteError.PAIRING_REJECTED))
        assertFalse(ConnectionIssues.isRecoverable(RemoteError.CERTIFICATE_CHANGED))
        assertFalse(ConnectionIssues.isRecoverable(RemoteError.UNSUPPORTED))
    }
}
