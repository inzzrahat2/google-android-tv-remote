package com.techport.tvremote.protocol.androidtv

import com.techport.tvremote.protocol.androidtv.wire.AppInfo
import com.techport.tvremote.protocol.androidtv.wire.AppLinkLaunchRequest
import com.techport.tvremote.protocol.androidtv.wire.DeviceInfo
import com.techport.tvremote.protocol.androidtv.wire.EditInfo
import com.techport.tvremote.protocol.androidtv.wire.FrameReader
import com.techport.tvremote.protocol.androidtv.wire.Framing
import com.techport.tvremote.protocol.androidtv.wire.ImeBatchEdit
import com.techport.tvremote.protocol.androidtv.wire.ImeKeyInject
import com.techport.tvremote.protocol.androidtv.wire.ImeObject
import com.techport.tvremote.protocol.androidtv.wire.ImeShowRequest
import com.techport.tvremote.protocol.androidtv.wire.KeyDirection
import com.techport.tvremote.protocol.androidtv.wire.KeyInject
import com.techport.tvremote.protocol.androidtv.wire.PingRequest
import com.techport.tvremote.protocol.androidtv.wire.PingResponse
import com.techport.tvremote.protocol.androidtv.wire.PoloConfiguration
import com.techport.tvremote.protocol.androidtv.wire.PoloEncoding
import com.techport.tvremote.protocol.androidtv.wire.PoloMessage
import com.techport.tvremote.protocol.androidtv.wire.PoloOptions
import com.techport.tvremote.protocol.androidtv.wire.PoloPairingRequest
import com.techport.tvremote.protocol.androidtv.wire.PoloPairingRequestAck
import com.techport.tvremote.protocol.androidtv.wire.ProtoException
import com.techport.tvremote.protocol.androidtv.wire.ProtoReader
import com.techport.tvremote.protocol.androidtv.wire.ProtoWriter
import com.techport.tvremote.protocol.androidtv.wire.RemoteConfigure
import com.techport.tvremote.protocol.androidtv.wire.RemoteMessage
import com.techport.tvremote.protocol.androidtv.wire.RemoteSetActive
import com.techport.tvremote.protocol.androidtv.wire.RemoteStart
import com.techport.tvremote.protocol.androidtv.wire.SetVolumeLevel
import com.techport.tvremote.protocol.androidtv.wire.TextFieldStatus
import com.techport.tvremote.protocol.androidtv.wire.VoiceBegin
import com.techport.tvremote.protocol.androidtv.wire.VoiceEnd
import com.techport.tvremote.protocol.androidtv.wire.VoicePayload
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WireCodecTest {

    @Test
    fun keyInjectHasExactReferenceBytes() {
        val bytes = RemoteMessage(keyInject = KeyInject(keyCode = 19, direction = KeyDirection.SHORT)).encode()
        assertArrayEquals(hex(0x52, 0x04, 0x08, 0x13, 0x10, 0x03), bytes)
    }

    @Test
    fun emptySubmessageIsStillPresent() {
        // proto3 presence: an all-default submessage must still be written (e.g. ping val1 = 0).
        assertArrayEquals(hex(0x4A, 0x00), RemoteMessage(pingResponse = PingResponse(0)).encode())
        assertEquals(PingResponse(0), RemoteMessage.decode(hex(0x4A, 0x00)).pingResponse)
    }

    @Test
    fun varintsRoundTripIncludingNegativeAndLarge() {
        for (value in listOf(0, 1, 127, 128, 300, 16_383, 16_384, Int.MAX_VALUE, -1, Int.MIN_VALUE)) {
            val bytes = ProtoWriter.encode { int(1, value) }
            if (value < 0) assertEquals(11, bytes.size) // tag + 10-byte sign-extended varint
            val r = ProtoReader(bytes)
            assertTrue(r.next())
            assertEquals(value, r.int())
        }
    }

    @Test
    fun remoteMessagesRoundTrip() {
        val messages = listOf(
            RemoteMessage(configure = RemoteConfigure(639, DeviceInfo("Shield", "NVIDIA", 1, "1", "com.example", "5.0"))),
            RemoteMessage(setActive = RemoteSetActive(623)),
            RemoteMessage(pingRequest = PingRequest(123456, 7)),
            RemoteMessage(pingResponse = PingResponse(123456)),
            RemoteMessage(keyInject = KeyInject(26, KeyDirection.START_LONG)),
            RemoteMessage(
                imeKeyInject = ImeKeyInject(
                    AppInfo(counter = 4, label = "Netflix", appPackage = "com.netflix.ninja"),
                    TextFieldStatus(3, "héllo", 1, 2, 0, "Search"),
                ),
            ),
            RemoteMessage(imeBatchEdit = ImeBatchEdit(5, 9, listOf(EditInfo(1, ImeObject(3, 3, "abcd"))))),
            RemoteMessage(imeShowRequest = ImeShowRequest(TextFieldStatus(value = "x", label = "Name"))),
            RemoteMessage(voiceBegin = VoiceBegin(42, "com.google.android.katniss")),
            RemoteMessage(voicePayload = VoicePayload(42, ByteArray(300) { it.toByte() })),
            RemoteMessage(voiceEnd = VoiceEnd(42)),
            RemoteMessage(start = RemoteStart(true)),
            RemoteMessage(setVolumeLevel = SetVolumeLevel("speaker", 100, 37, true)),
            RemoteMessage(appLinkLaunch = AppLinkLaunchRequest("https://www.youtube.com")),
        )
        for (m in messages) assertEquals(m, RemoteMessage.decode(m.encode()))
    }

    @Test
    fun unknownFieldsAreSkipped() {
        val bytes = ProtoWriter.encode {
            int(99, 5) // unknown varint
            bytes(77, byteArrayOf(1, 2, 3)) // unknown length-delimited
            message(50) {
                int(1, 11) // unknown1 inside SetVolumeLevel
                string(3, "tv")
                int(6, 100)
                int(7, 12)
            }
        } + hex(0xED, 0x01, 0x01, 0x02, 0x03, 0x04) // field 29, fixed32
        val decoded = RemoteMessage.decode(bytes)
        assertEquals(SetVolumeLevel("tv", 100, 12, false), decoded.setVolumeLevel)
    }

    @Test
    fun truncatedInputIsProtocolError() {
        val full = RemoteMessage(appLinkLaunch = AppLinkLaunchRequest("https://example.com")).encode()
        for (cut in 1 until full.size) {
            try {
                RemoteMessage.decode(full.copyOf(cut))
                fail("Decoded a message truncated at $cut")
            } catch (expected: ProtoException) {
                // ok
            }
        }
    }

    @Test
    fun poloMessagesRoundTrip() {
        val encoding = PoloEncoding(3, 6)
        val messages = listOf(
            PoloMessage(pairingRequest = PoloPairingRequest("atvremote", "TV Remote")),
            PoloMessage(pairingRequestAck = PoloPairingRequestAck("Living room")),
            PoloMessage(options = PoloOptions(listOf(encoding), emptyList(), 1)),
            PoloMessage(configuration = PoloConfiguration(encoding, 1)),
            PoloMessage(configurationAck = true),
            PoloMessage(status = 402),
        )
        for (m in messages) {
            val d = PoloMessage.decode(m.encode())
            assertEquals(2, d.protocolVersion)
            assertEquals(m.status, d.status)
            assertEquals(m.pairingRequest, d.pairingRequest)
            assertEquals(m.pairingRequestAck, d.pairingRequestAck)
            assertEquals(m.options, d.options)
            assertEquals(m.configuration, d.configuration)
            assertEquals(m.configurationAck, d.configurationAck)
        }
        val secret = ByteArray(32) { (it * 7).toByte() }
        assertArrayEquals(secret, PoloMessage.decode(PoloMessage(secret = secret).encode()).secret)
        assertArrayEquals(secret, PoloMessage.decode(PoloMessage(secretAck = secret).encode()).secretAck)
        assertNull(PoloMessage.decode(PoloMessage(secret = secret).encode()).secretAck)
    }

    @Test
    fun poloEnvelopeMatchesReferenceBytes() {
        // protocol_version=2, status=200, configuration_ack{}
        assertArrayEquals(hex(0x08, 0x02, 0x10, 0xC8, 0x01, 0xFA, 0x01, 0x00), PoloMessage(configurationAck = true).encode())
    }

    @Test
    fun framesSurviveByteByByteDelivery() {
        val big = RemoteMessage(voicePayload = VoicePayload(1, ByteArray(20_000) { it.toByte() })).encode()
        val small = RemoteMessage(keyInject = KeyInject(19, 3)).encode()
        val stream = Framing.frame(small) + Framing.frame(big) + Framing.frame(small)
        val reader = FrameReader(OneByteAtATime(stream))
        assertArrayEquals(small, reader.readFrame())
        assertArrayEquals(big, reader.readFrame()) // 2-byte length varint split across reads
        assertArrayEquals(small, reader.readFrame())
        try {
            reader.readFrame()
            fail("Expected EOF")
        } catch (expected: EOFException) {
            // ok
        }
    }

    @Test
    fun truncatedFrameIsEof() {
        val frame = Framing.frame(ByteArray(10))
        try {
            FrameReader(ByteArrayInputStream(frame.copyOf(6))).readFrame()
            fail("Expected EOF")
        } catch (expected: EOFException) {
            // ok
        }
    }

    @Test(expected = ProtoException::class)
    fun oversizedFrameIsRejected() {
        FrameReader(ByteArrayInputStream(Framing.frame(ByteArray(100))), maxFrameBytes = 50).readFrame()
    }

    private class OneByteAtATime(private val data: ByteArray) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val c = read()
            if (c < 0) return -1
            b[off] = c.toByte()
            return 1
        }
    }
}
