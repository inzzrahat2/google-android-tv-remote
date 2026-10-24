package com.techport.tvremote.protocol.androidtv

import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.RemoteException
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.protocol.androidtv.wire.AppInfo
import com.techport.tvremote.protocol.androidtv.wire.AppLinkLaunchRequest
import com.techport.tvremote.protocol.androidtv.wire.DeviceInfo
import com.techport.tvremote.protocol.androidtv.wire.EditInfo
import com.techport.tvremote.protocol.androidtv.wire.Feature
import com.techport.tvremote.protocol.androidtv.wire.ImeBatchEdit
import com.techport.tvremote.protocol.androidtv.wire.ImeKeyInject
import com.techport.tvremote.protocol.androidtv.wire.ImeObject
import com.techport.tvremote.protocol.androidtv.wire.ImeShowRequest
import com.techport.tvremote.protocol.androidtv.wire.KeyDirection
import com.techport.tvremote.protocol.androidtv.wire.KeyInject
import com.techport.tvremote.protocol.androidtv.wire.PingRequest
import com.techport.tvremote.protocol.androidtv.wire.PingResponse
import com.techport.tvremote.protocol.androidtv.wire.RemoteConfigure
import com.techport.tvremote.protocol.androidtv.wire.RemoteMessage
import com.techport.tvremote.protocol.androidtv.wire.RemoteSetActive
import com.techport.tvremote.protocol.androidtv.wire.RemoteStart
import com.techport.tvremote.protocol.androidtv.wire.SetVolumeLevel
import com.techport.tvremote.protocol.androidtv.wire.TextFieldStatus
import com.techport.tvremote.protocol.androidtv.wire.VoiceBegin
import com.techport.tvremote.protocol.androidtv.wire.VoiceEnd
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTvSessionTest {

    private val transport = FakeTransport()
    private val ourInfo = DeviceInfo(model = "Pixel", vendor = "Google", unknown1 = 1, unknown2 = "1", packageName = "atvremote", appVersion = "1.0.0")

    /** Everything a typical Google TV advertises, plus an unknown bit (16). */
    private val tvFeatures = AndroidTvSession.DEFAULT_FEATURES or (1 shl 4)

    private fun TestScope.session(features: Int = AndroidTvSession.DEFAULT_FEATURES) = AndroidTvSession(
        transport = transport,
        logger = AppLogger(),
        connectedHost = "192.168.1.20",
        connectedPort = 6466,
        requestedFeatures = features,
        deviceInfo = ourInfo,
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    private suspend fun TestScope.openReady(features: Int = AndroidTvSession.DEFAULT_FEATURES, tv: Int = tvFeatures): AndroidTvSession {
        val s = session(features)
        s.start()
        val ready = async { s.awaitReady() }
        transport.push(RemoteMessage(configure = RemoteConfigure(tv, DeviceInfo(model = "Chromecast", vendor = "Google"))))
        val configureReply = transport.next().configure!!
        assertEquals(features and tv, configureReply.code1)
        assertEquals(ourInfo, configureReply.deviceInfo)

        transport.push(RemoteMessage(setActive = RemoteSetActive(0)))
        assertEquals(RemoteSetActive(features and tv), transport.next().setActive)
        runCurrent()
        assertFalse("Ready before remote_start", ready.isCompleted)

        transport.push(RemoteMessage(start = RemoteStart(true)))
        ready.await()
        return s
    }

    @Test
    fun handshakeNegotiatesFeaturesAndBecomesReady() = runTest {
        val s = openReady()
        val caps = s.capabilities.value
        assertTrue(caps.dpad && caps.navigation && caps.mediaControls && caps.keyboard && caps.voice)
        assertTrue(caps.power && caps.appLaunch && caps.volumeLevel)
        assertEquals(true, s.screenOn.value)
        s.close()
    }

    @Test
    fun capabilitiesFollowTvFeatures() = runTest {
        val s = openReady(tv = Feature.PING or Feature.KEY)
        val caps = s.capabilities.value
        assertTrue(caps.dpad && caps.volume && caps.mute && caps.inputSwitching)
        assertFalse(caps.keyboard || caps.voice || caps.power || caps.appLaunch || caps.volumeLevel)
        try {
            s.execute(RemoteCommand.LaunchApp("https://example.com"))
            fail("Expected UNSUPPORTED")
        } catch (e: RemoteException) {
            assertEquals(RemoteError.UNSUPPORTED, e.error)
        }
        s.close()
    }

    @Test
    fun answersEveryPing() = runTest {
        val s = openReady()
        for (v in listOf(1, 77, 123_456)) {
            transport.push(RemoteMessage(pingRequest = PingRequest(v, 0)))
            assertEquals(RemoteMessage(pingResponse = PingResponse(v)), transport.next())
        }
        s.close()
    }

    @Test
    fun keysUseReferenceEncoding() = runTest {
        val s = openReady()
        s.execute(RemoteCommand.Key(RemoteKey.DPAD_UP))
        assertArrayEquals(hex(0x52, 0x04, 0x08, 0x13, 0x10, 0x03), transport.toTv.receive())
        s.execute(RemoteCommand.Key(RemoteKey.DPAD_CENTER, KeyAction.DOWN))
        assertEquals(KeyInject(23, KeyDirection.START_LONG), transport.next().keyInject)
        s.execute(RemoteCommand.Key(RemoteKey.DPAD_CENTER, KeyAction.UP))
        assertEquals(KeyInject(23, KeyDirection.END_LONG), transport.next().keyInject)
        s.execute(RemoteCommand.LaunchApp("https://www.netflix.com/title/1"))
        assertEquals(AppLinkLaunchRequest("https://www.netflix.com/title/1"), transport.next().appLinkLaunch)
        s.close()
    }

    @Test
    fun parsesVolume() = runTest {
        val s = openReady(tv = Feature.PING or Feature.KEY)
        assertNull(s.volume.value)
        transport.push(RemoteMessage(setVolumeLevel = SetVolumeLevel("tv", volumeMax = 100, volumeLevel = 25, volumeMuted = true)))
        runCurrent()
        assertEquals(VolumeState(25, 100, true), s.volume.value)
        assertTrue("A reported level enables the level display", s.capabilities.value.volumeLevel)
        s.close()
    }

    @Test
    fun tracksTextFieldAndSendsTextWithLatestCounters() = runTest {
        val s = openReady()
        transport.push(RemoteMessage(imeShowRequest = ImeShowRequest(TextFieldStatus(value = "ab", label = "Search"))))
        runCurrent()
        assertEquals(TvTextField("ab", "Search"), s.textField.value)

        transport.push(RemoteMessage(imeBatchEdit = ImeBatchEdit(7, 3, listOf(EditInfo(1, ImeObject(2, 2, "abc"))))))
        runCurrent()
        assertEquals(TvTextField("abc", "Search"), s.textField.value)

        s.execute(RemoteCommand.Text("héllo"))
        val edit = transport.next().imeBatchEdit!!
        assertEquals(7, edit.imeCounter)
        assertEquals(3, edit.fieldCounter)
        assertEquals(listOf(EditInfo(1, ImeObject(4, 4, "héllo"))), edit.editInfo)
        s.close()
    }

    @Test
    fun appChangeUpdatesCurrentAppAndDropsField() = runTest {
        val s = openReady()
        transport.push(RemoteMessage(imeShowRequest = ImeShowRequest(TextFieldStatus(value = "", label = "Search"))))
        runCurrent()
        assertNotNull(s.textField.value)
        transport.push(RemoteMessage(imeKeyInject = ImeKeyInject(AppInfo(appPackage = "com.netflix.ninja"))))
        runCurrent()
        assertEquals("com.netflix.ninja", s.currentApp.value)
        assertNull(s.textField.value)
        s.close()
    }

    @Test
    fun voiceSessionStreamsChunks() = runTest {
        val s = openReady()
        val start = async { s.startVoice() }
        assertEquals(KeyInject(RemoteKey.SEARCH.keyCode, KeyDirection.SHORT), transport.next().keyInject)
        transport.push(RemoteMessage(voiceBegin = VoiceBegin(42, "com.google.android.katniss")))
        assertEquals(VoiceBegin(42), transport.next().voiceBegin)
        val voice = start.await()!!

        voice.send(ByteArray(30_000) { 1 })
        val first = transport.next().voicePayload!!
        val second = transport.next().voicePayload!!
        assertEquals(42, first.sessionId)
        assertEquals(20 * 1024, first.samples.size)
        assertEquals(30_000 - 20 * 1024, second.samples.size)

        voice.send(ByteArray(100) { 2 })
        assertTrue("Small pieces are buffered", transport.nothingSent())
        voice.end()
        val padded = transport.next().voicePayload!!
        assertEquals(8 * 1024, padded.samples.size)
        assertEquals(2.toByte(), padded.samples[99])
        assertEquals(0.toByte(), padded.samples[100])
        assertEquals(VoiceEnd(42), transport.next().voiceEnd)
        s.close()
    }

    @Test
    fun voiceWithoutBeginIsHidden() = runTest {
        val s = openReady()
        val start = async { s.startVoice() }
        transport.next() // KEYCODE_SEARCH
        advanceTimeBy(AndroidTvSession.VOICE_BEGIN_TIMEOUT_MS + 1)
        // Keep the idle watchdog satisfied while time passes.
        transport.push(RemoteMessage(pingRequest = PingRequest(1)))
        assertNull(start.await())
        assertFalse(s.capabilities.value.voice)
        s.close()
    }

    @Test
    fun voiceUnavailableWhenNotNegotiated() = runTest {
        val s = openReady(tv = Feature.PING or Feature.KEY)
        assertNull(s.startVoice())
        assertTrue(transport.nothingSent())
        s.close()
    }

    @Test
    fun idleWatchdogClosesSilentSession() = runTest {
        val s = openReady()
        advanceTimeBy(10_000)
        transport.push(RemoteMessage(pingRequest = PingRequest(5)))
        transport.next()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse("A ping resets the watchdog", transport.closed)
        advanceTimeBy(AndroidTvSession.IDLE_TIMEOUT_MS)
        runCurrent()
        assertEquals(RemoteError.CONNECTION_LOST, s.awaitClosed())
        assertTrue(transport.closed)
    }

    @Test
    fun eofAfterReadyIsConnectionLost() = runTest {
        val s = openReady()
        transport.fromTv.close()
        assertEquals(RemoteError.CONNECTION_LOST, s.awaitClosed())
        try {
            s.execute(RemoteCommand.Key(RemoteKey.HOME))
            fail("Expected CONNECTION_LOST")
        } catch (e: RemoteException) {
            assertEquals(RemoteError.CONNECTION_LOST, e.error)
        }
    }

    @Test
    fun malformedDataBeforeReadyFailsOpen() = runTest {
        val s = session()
        s.start()
        val ready = async { runCatching { s.awaitReady() } }
        transport.fromTv.send(hex(0x0A, 0x05, 0x08))
        val error = ready.await().exceptionOrNull() as RemoteException
        assertEquals(RemoteError.PROTOCOL_ERROR, error.error)
        assertEquals(RemoteError.PROTOCOL_ERROR, s.awaitClosed())
        assertTrue(transport.closed)
    }

    @Test
    fun silentTvDuringHandshakeTimesOut() = runTest {
        val s = session()
        s.start()
        val ready = async { runCatching { s.awaitReady() } }
        advanceTimeBy(AndroidTvSession.IDLE_TIMEOUT_MS + 1)
        assertEquals(RemoteError.TIMEOUT, (ready.await().exceptionOrNull() as RemoteException).error)
    }

    @Test
    fun closeIsIdempotentAndDeliberate() = runTest {
        val s = openReady()
        s.close()
        s.close()
        assertNull(s.awaitClosed())
        assertTrue(transport.closed)
    }

    @Test
    fun capabilityMappingTable() {
        val none = AndroidTvSession.capabilitiesFor(0, volumeReported = false, voiceUnavailable = false)
        assertFalse(none.dpad || none.keyboard || none.voice || none.power || none.appLaunch || none.volumeLevel)
        val all = AndroidTvSession.capabilitiesFor(AndroidTvSession.DEFAULT_FEATURES, false, false)
        assertTrue(all.dpad && all.touchpad && all.keyboard && all.voice && all.power && all.appLaunch && all.volumeLevel)
        assertFalse(AndroidTvSession.capabilitiesFor(AndroidTvSession.DEFAULT_FEATURES, false, voiceUnavailable = true).voice)
    }
}
