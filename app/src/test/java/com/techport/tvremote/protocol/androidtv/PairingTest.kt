package com.techport.tvremote.protocol.androidtv

import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.CodeAlphabet
import com.techport.tvremote.domain.model.DiscoveredDevice
import com.techport.tvremote.domain.model.PairingState
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.protocol.androidtv.wire.PoloConfiguration
import com.techport.tvremote.protocol.androidtv.wire.PoloEncoding
import com.techport.tvremote.protocol.androidtv.wire.PoloMessage
import com.techport.tvremote.protocol.androidtv.wire.PoloOptions
import com.techport.tvremote.protocol.androidtv.wire.PoloPairingRequestAck
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingTest {

    private val clientKey = rsaKey()
    private val serverKey = rsaKey()
    private val target = DiscoveredDevice("atv:bt:aa:bb:cc:dd:ee:ff", "Living Room", "192.168.1.20", 6466, AndroidTvProtocol.ID)

    // --- Secret computation -------------------------------------------------------------------

    @Test
    fun hexBytesPadsOddLengthHex() {
        assertArrayEquals(hex(0x01, 0x00, 0x01), PairingSecret.hexBytes(BigInteger.valueOf(65_537))) // "10001"
        assertArrayEquals(hex(0x0A, 0xBC), PairingSecret.hexBytes(BigInteger.valueOf(0xABC)))
        assertArrayEquals(hex(0xAB, 0xCD), PairingSecret.hexBytes(BigInteger.valueOf(0xABCD)))
        // Top bit set: no sign byte, unlike BigInteger.toByteArray().
        assertArrayEquals(hex(0x80, 0x00), PairingSecret.hexBytes(BigInteger.valueOf(0x8000)))
    }

    @Test
    fun secretMatchesIndependentComputation() {
        val code = validCode(clientKey, serverKey, "1A2B")
        val secret = PairingSecret.compute(clientKey, serverKey, code)
        assertArrayEquals(referenceHash(clientKey, serverKey, "1A2B"), secret)
    }

    @Test
    fun secretWithOddLengthModulusAndExponent() {
        val client = FakeRsaKey(BigInteger("ABCDE", 16), BigInteger.valueOf(65_537))
        val server = FakeRsaKey(BigInteger("123", 16), BigInteger.valueOf(3))
        val code = validCode(client, server, "00FF")
        assertArrayEquals(referenceHash(client, server, "00FF"), PairingSecret.compute(client, server, code))
    }

    @Test
    fun wrongChecksumIsRejectedLocally() {
        val code = validCode(clientKey, serverKey, "C0DE")
        val wrong = flipChecksum(code)
        assertNull(PairingSecret.compute(clientKey, serverKey, wrong))
    }

    @Test
    fun normalizeAcceptsOnlySixHexDigits() {
        assertEquals("A1B2C3", PairingSecret.normalize(" a1b2c3 "))
        assertNull(PairingSecret.normalize("A1B2C"))
        assertNull(PairingSecret.normalize("A1B2C3D"))
        assertNull(PairingSecret.normalize("G1B2C3"))
    }

    // --- Pairing session ----------------------------------------------------------------------

    @Test
    fun fullPairingPinsCertificate() = runTest {
        val transport = FakeTransport()
        val pins = InMemoryPinStore()
        val session = pairing(transport, pins)

        val tv = launch { tvHandshake(transport) }
        session.start()
        tv.join()
        assertEquals(PairingState.AwaitingCode(6, CodeAlphabet.HEX, attemptFailed = false), session.state.value)

        val code = validCode(clientKey, serverKey, "4F2E")
        val tvSecret = launch {
            val secret = PoloMessage.decode(transport.toTv.receive()).secret
            assertArrayEquals(referenceHash(clientKey, serverKey, "4F2E"), secret)
            transport.fromTv.send(PoloMessage(secretAck = secret).encode())
        }
        session.submitCode(code.lowercase())
        tvSecret.join()

        val paired = session.state.value as PairingState.Paired
        assertEquals(target.id, paired.device.id)
        assertEquals(6466, paired.device.port)
        assertEquals(AndroidTvProtocol.ID, paired.device.protocolId)
        assertEquals("cafebabe", pins.pins[target.id])
        assertTrue(transport.closed)
    }

    @Test
    fun mistypedCodeIsCaughtWithoutSending() = runTest {
        val transport = FakeTransport()
        val session = pairing(transport, InMemoryPinStore())
        val tv = launch { tvHandshake(transport) }
        session.start()
        tv.join()

        session.submitCode(flipChecksum(validCode(clientKey, serverKey, "1234")))
        assertEquals(PairingState.AwaitingCode(6, CodeAlphabet.HEX, attemptFailed = true), session.state.value)
        assertTrue(transport.nothingSent())

        session.submitCode("12") // too short
        assertEquals(PairingState.AwaitingCode(6, CodeAlphabet.HEX, attemptFailed = true), session.state.value)
        assertTrue(transport.nothingSent())
    }

    @Test
    fun badSecretStatusFailsAsRejected() = runTest {
        val transport = FakeTransport()
        val pins = InMemoryPinStore()
        val session = pairing(transport, pins)
        val tv = launch { tvHandshake(transport) }
        session.start()
        tv.join()

        val tvSecret = launch {
            transport.toTv.receive()
            transport.fromTv.send(PoloMessage(status = 402).encode())
        }
        session.submitCode(validCode(clientKey, serverKey, "ABCD"))
        tvSecret.join()
        assertEquals(PairingState.Failed(RemoteError.PAIRING_REJECTED), session.state.value)
        assertTrue(pins.pins.isEmpty())
        assertTrue(transport.closed)
    }

    @Test
    fun silentTvTimesOut() = runTest {
        val transport = FakeTransport()
        val session = pairing(transport, InMemoryPinStore())
        session.start() // The TV never answers the pairing request.
        assertEquals(PairingState.Failed(RemoteError.TIMEOUT), session.state.value)
        assertTrue(transport.closed)
    }

    @Test
    fun cancelReturnsToIdleAndCloses() = runTest {
        val transport = FakeTransport()
        val session = pairing(transport, InMemoryPinStore())
        val tv = launch { tvHandshake(transport) }
        session.start()
        tv.join()
        session.cancel()
        assertEquals(PairingState.Idle, session.state.value)
        assertTrue(transport.closed)
    }

    private fun TestScope.pairing(transport: FakeTransport, pins: InMemoryPinStore) = AndroidTvPairingSession(
        target = target,
        clientName = "TV Remote",
        logger = AppLogger(),
        pins = pins,
        openChannel = { SecureChannel(transport, clientKey, serverKey, "cafebabe") },
    )

    private suspend fun tvHandshake(t: FakeTransport) {
        val request = PoloMessage.decode(t.toTv.receive())
        assertEquals(2, request.protocolVersion)
        assertEquals(200, request.status)
        assertEquals("atvremote", request.pairingRequest?.serviceName)
        assertEquals("TV Remote", request.pairingRequest?.clientName)
        t.fromTv.send(PoloMessage(pairingRequestAck = PoloPairingRequestAck("Living Room")).encode())

        val options = PoloMessage.decode(t.toTv.receive()).options
        assertEquals(PoloOptions(listOf(PoloEncoding(3, 6)), emptyList(), 1), options)
        t.fromTv.send(PoloMessage(options = PoloOptions(listOf(PoloEncoding(3, 6)), emptyList(), 1)).encode())

        assertEquals(PoloConfiguration(PoloEncoding(3, 6), 1), PoloMessage.decode(t.toTv.receive()).configuration)
        t.fromTv.send(PoloMessage(configurationAck = true).encode())
    }

    companion object {
        fun rsaKey(): RSAPublicKey =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey

        /** Unsigned big-endian bytes via BigInteger.toByteArray(), independent of the code under test. */
        private fun unsigned(v: BigInteger): ByteArray {
            val raw = v.toByteArray()
            return if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        }

        fun referenceHash(client: RSAPublicKey, server: RSAPublicKey, suffix: String): ByteArray =
            MessageDigest.getInstance("SHA-256").run {
                update(unsigned(client.modulus))
                update(unsigned(client.publicExponent))
                update(unsigned(server.modulus))
                update(unsigned(server.publicExponent))
                update(byteArrayOf(suffix.substring(0, 2).toInt(16).toByte(), suffix.substring(2, 4).toInt(16).toByte()))
                digest()
            }

        /** The code the TV would show for this suffix: checksum byte + suffix. */
        fun validCode(client: RSAPublicKey, server: RSAPublicKey, suffix: String): String =
            "%02X".format(referenceHash(client, server, suffix)[0].toInt() and 0xFF) + suffix.uppercase()

        fun flipChecksum(code: String): String {
            val wrong = (code.substring(0, 2).toInt(16) + 1) and 0xFF
            return "%02X".format(wrong) + code.substring(2)
        }
    }
}
