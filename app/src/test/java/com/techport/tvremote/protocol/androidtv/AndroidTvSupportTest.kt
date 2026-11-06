package com.techport.tvremote.protocol.androidtv

import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.protocol.androidtv.wire.ProtoException
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTvSupportTest {

    @Test
    fun hostValidation() {
        assertEquals("192.168.1.20", HostValidation.normalize(" 192.168.1.20 "))
        assertEquals("living-room-tv.local", HostValidation.normalize("Living-Room-TV.local."))
        assertEquals("fe80::1%wlan0", HostValidation.normalize("[fe80::1%wlan0]"))
        assertEquals("2001:db8::8a2e:370:7334", HostValidation.normalize("2001:DB8::8A2E:370:7334"))
        assertEquals("::ffff:192.168.1.2", HostValidation.normalize("::ffff:192.168.1.2"))
        assertEquals("1:2:3:4:5:6:7:8", HostValidation.normalize("1:2:3:4:5:6:7:8"))

        for (bad in listOf(
            "", " ", "256.1.1.1", "192.168.1", "192.168.01.1", "1.2.3.4.5", "-tv.local", "tv-.local",
            "tv_box", "a..b", "1:2:3:4:5:6:7:8:9", "1::2::3", "12345::1", ":1:2:3:4:5:6:7", "::ffff:999.1.1.1",
            "fe80::1%", "http://192.168.1.2",
        )) {
            assertNull("'$bad' should be rejected", HostValidation.normalize(bad))
        }
        assertTrue(HostValidation.isValidPort(6466))
        assertFalse(HostValidation.isValidPort(0))
        assertFalse(HostValidation.isValidPort(70_000))
    }

    @Test
    fun deviceIdPrefersBluetoothMac() {
        assertEquals("atv:bt:aa:bb:cc:dd:ee:ff", AndroidTvDiscovery.deviceIdFor("Living Room", "AA:BB:CC:DD:EE:FF"))
        assertEquals("atv:name:Living Room", AndroidTvDiscovery.deviceIdFor("Living Room", null))
        assertEquals("atv:name:Living Room", AndroidTvDiscovery.deviceIdFor("Living Room", "not-a-mac"))
        assertEquals("atv:name:Living Room", AndroidTvDiscovery.deviceIdFor("Living Room", "00:00:00:00:00:00"))
    }

    @Test
    fun errorMapping() {
        assertEquals(RemoteError.UNREACHABLE, mapIoError(ConnectException(), established = false))
        assertEquals(RemoteError.UNREACHABLE, mapIoError(NoRouteToHostException(), established = false))
        assertEquals(RemoteError.UNREACHABLE, mapIoError(UnknownHostException(), established = false))
        assertEquals(RemoteError.TIMEOUT, mapIoError(SocketTimeoutException(), established = false))
        assertEquals(RemoteError.CONNECTION_LOST, mapIoError(SocketTimeoutException(), established = true))
        assertEquals(RemoteError.PAIRING_REJECTED, mapIoError(SSLHandshakeException("bad_certificate"), established = false))
        assertEquals(RemoteError.CONNECTION_LOST, mapIoError(SSLException("reset"), established = true))
        // A TV waking up may reset mid-handshake: retryable, not a reason to re-pair.
        val reset = SSLHandshakeException("Connection closed by peer").apply { initCause(SocketException("reset")) }
        assertEquals(RemoteError.CONNECTION_LOST, mapIoError(reset, established = false))
        assertEquals(RemoteError.CONNECTION_LOST, mapIoError(SSLException("Read error"), established = false))
        val pinned = SSLHandshakeException("untrusted").apply { initCause(CertificatePinMismatchException()) }
        assertEquals(RemoteError.CERTIFICATE_CHANGED, mapIoError(pinned, established = false))
        assertEquals(RemoteError.PROTOCOL_ERROR, mapIoError(ProtoException("bad"), established = true))
        assertEquals(RemoteError.CONNECTION_LOST, mapIoError(EOFException(), established = true))
        assertEquals(RemoteError.CONNECTION_LOST, mapIoError(SocketException("reset"), established = true))
    }

    @Test
    fun pinningTrustManagerEnforcesPin() {
        // A null chain is rejected outright, pinned or not.
        for (pin in listOf(null, "00")) {
            try {
                PinningTrustManager(pin).checkServerTrusted(null, "RSA")
                throw AssertionError("Accepted an empty chain")
            } catch (expected: java.security.cert.CertificateException) {
                // ok
            }
        }
    }

    @Test
    fun voiceChunkerKeepsSpeechContiguous() {
        val chunker = VoiceChunker(minBytes = 4, maxBytes = 6)
        assertTrue(chunker.add(byteArrayOf(1, 2)).isEmpty())
        val out = chunker.add(byteArrayOf(3, 4, 5, 6, 7, 8, 9))
        assertEquals(listOf(listOf<Byte>(1, 2, 3, 4, 5, 6)), out.map { it.toList() })
        assertEquals(listOf<Byte>(7, 8, 9, 0), chunker.flush()!!.toList())
        assertNull(chunker.flush())
    }
}
