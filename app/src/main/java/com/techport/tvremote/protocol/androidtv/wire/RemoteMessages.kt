package com.techport.tvremote.protocol.androidtv.wire

/*
 * Remote-session messages, port 6466. Field numbers follow the community-documented
 * remotemessage.proto (tronikos/androidtvremote2). Google has not published this schema;
 * fields named unknownN there are not understood and are skipped here.
 */

/** RemoteDirection. */
internal object KeyDirection {
    const val START_LONG = 1
    const val END_LONG = 2
    const val SHORT = 3
}

/** Feature bits exchanged in RemoteConfigure.code1 / RemoteSetActive.active. */
internal object Feature {
    const val PING = 1
    const val KEY = 1 shl 1
    const val IME = 1 shl 2
    const val VOICE = 1 shl 3
    const val POWER = 1 shl 5
    const val VOLUME = 1 shl 6
    const val APP_LINK = 1 shl 9
}

internal data class DeviceInfo(
    val model: String = "",
    val vendor: String = "",
    val unknown1: Int = 0,
    val unknown2: String = "",
    val packageName: String = "",
    val appVersion: String = "",
)

internal data class RemoteConfigure(val code1: Int, val deviceInfo: DeviceInfo? = null)
internal data class RemoteSetActive(val active: Int)
internal data class RemoteErrorMessage(val value: Boolean)
internal data class PingRequest(val val1: Int, val val2: Int = 0)
internal data class PingResponse(val val1: Int)
internal data class KeyInject(val keyCode: Int, val direction: Int)

internal data class TextFieldStatus(
    val counterField: Int = 0,
    val value: String = "",
    val start: Int = 0,
    val end: Int = 0,
    val int5: Int = 0,
    val label: String = "",
)

internal data class AppInfo(val counter: Int = 0, val label: String = "", val appPackage: String = "")
internal data class ImeKeyInject(val appInfo: AppInfo? = null, val textFieldStatus: TextFieldStatus? = null)
internal data class ImeObject(val start: Int = 0, val end: Int = 0, val value: String = "")
internal data class EditInfo(val insert: Int = 0, val textFieldStatus: ImeObject? = null)
internal data class ImeBatchEdit(val imeCounter: Int = 0, val fieldCounter: Int = 0, val editInfo: List<EditInfo> = emptyList())
internal data class ImeShowRequest(val textFieldStatus: TextFieldStatus? = null)
internal data class VoiceBegin(val sessionId: Int, val packageName: String = "")
internal data class VoiceEnd(val sessionId: Int)
internal data class RemoteStart(val started: Boolean)

internal data class SetVolumeLevel(
    val playerModel: String = "",
    val volumeMax: Int = 0,
    val volumeLevel: Int = 0,
    val volumeMuted: Boolean = false,
)

internal data class AppLinkLaunchRequest(val appLink: String)

/** Voice audio. Not a data class: compares content, and [toString] never prints samples. */
internal class VoicePayload(val sessionId: Int, val samples: ByteArray) {
    override fun equals(other: Any?) =
        other is VoicePayload && other.sessionId == sessionId && other.samples.contentEquals(samples)

    override fun hashCode() = 31 * sessionId + samples.contentHashCode()
    override fun toString() = "VoicePayload(session=$sessionId, ${samples.size} bytes)"
}

/** RemoteMessage: normally exactly one field is set. */
internal data class RemoteMessage(
    val configure: RemoteConfigure? = null,
    val setActive: RemoteSetActive? = null,
    val error: RemoteErrorMessage? = null,
    val pingRequest: PingRequest? = null,
    val pingResponse: PingResponse? = null,
    val keyInject: KeyInject? = null,
    val imeKeyInject: ImeKeyInject? = null,
    val imeBatchEdit: ImeBatchEdit? = null,
    val imeShowRequest: ImeShowRequest? = null,
    val voiceBegin: VoiceBegin? = null,
    val voicePayload: VoicePayload? = null,
    val voiceEnd: VoiceEnd? = null,
    val start: RemoteStart? = null,
    val setVolumeLevel: SetVolumeLevel? = null,
    val appLinkLaunch: AppLinkLaunchRequest? = null,
) {
    fun encode(): ByteArray = ProtoWriter.encode {
        configure?.let { c ->
            message(1) {
                intIfSet(1, c.code1)
                c.deviceInfo?.let { d -> message(2) { deviceInfo(d) } }
            }
        }
        setActive?.let { message(2) { intIfSet(1, it.active) } }
        error?.let { message(3) { boolIfSet(1, it.value) } }
        pingRequest?.let {
            message(8) {
                intIfSet(1, it.val1)
                intIfSet(2, it.val2)
            }
        }
        pingResponse?.let { message(9) { intIfSet(1, it.val1) } }
        keyInject?.let {
            message(10) {
                intIfSet(1, it.keyCode)
                intIfSet(2, it.direction)
            }
        }
        imeKeyInject?.let { k ->
            message(20) {
                k.appInfo?.let { a ->
                    message(1) {
                        intIfSet(1, a.counter)
                        stringIfSet(10, a.label)
                        stringIfSet(12, a.appPackage)
                    }
                }
                k.textFieldStatus?.let { message(2) { textFieldStatus(it) } }
            }
        }
        imeBatchEdit?.let { b ->
            message(21) {
                intIfSet(1, b.imeCounter)
                intIfSet(2, b.fieldCounter)
                b.editInfo.forEach { e ->
                    message(3) {
                        intIfSet(1, e.insert)
                        e.textFieldStatus?.let { o ->
                            message(2) {
                                intIfSet(1, o.start)
                                intIfSet(2, o.end)
                                stringIfSet(3, o.value)
                            }
                        }
                    }
                }
            }
        }
        imeShowRequest?.let { s -> message(22) { s.textFieldStatus?.let { message(2) { textFieldStatus(it) } } } }
        voiceBegin?.let {
            message(30) {
                intIfSet(1, it.sessionId)
                stringIfSet(2, it.packageName)
            }
        }
        voicePayload?.let {
            message(31) {
                intIfSet(1, it.sessionId)
                if (it.samples.isNotEmpty()) bytes(2, it.samples)
            }
        }
        voiceEnd?.let { message(32) { intIfSet(1, it.sessionId) } }
        start?.let { message(40) { boolIfSet(1, it.started) } }
        setVolumeLevel?.let { v ->
            message(50) {
                stringIfSet(3, v.playerModel)
                intIfSet(6, v.volumeMax)
                intIfSet(7, v.volumeLevel)
                boolIfSet(8, v.volumeMuted)
            }
        }
        appLinkLaunch?.let { message(90) { stringIfSet(1, it.appLink) } }
    }

    /** Name of the set field, for logs. Never includes content (text, audio). */
    fun kind(): String = when {
        configure != null -> "configure"
        setActive != null -> "set_active"
        error != null -> "error"
        pingRequest != null -> "ping_request"
        pingResponse != null -> "ping_response"
        keyInject != null -> "key_inject"
        imeKeyInject != null -> "ime_key_inject"
        imeBatchEdit != null -> "ime_batch_edit"
        imeShowRequest != null -> "ime_show_request"
        voiceBegin != null -> "voice_begin"
        voicePayload != null -> "voice_payload"
        voiceEnd != null -> "voice_end"
        start != null -> "start"
        setVolumeLevel != null -> "set_volume_level"
        appLinkLaunch != null -> "app_link_launch_request"
        else -> "unhandled"
    }

    override fun toString() = "RemoteMessage(${kind()})"

    companion object {
        fun decode(bytes: ByteArray): RemoteMessage {
            var m = RemoteMessage()
            ProtoReader(bytes).forEachField { field ->
                m = when (field) {
                    1 -> m.copy(configure = decodeConfigure(message()))
                    2 -> m.copy(setActive = RemoteSetActive(singleInt(message(), 1)))
                    3 -> m.copy(error = decodeError(message()))
                    8 -> m.copy(pingRequest = decodePingRequest(message()))
                    9 -> m.copy(pingResponse = PingResponse(singleInt(message(), 1)))
                    10 -> m.copy(keyInject = decodeKeyInject(message()))
                    20 -> m.copy(imeKeyInject = decodeImeKeyInject(message()))
                    21 -> m.copy(imeBatchEdit = decodeBatchEdit(message()))
                    22 -> m.copy(imeShowRequest = decodeShowRequest(message()))
                    30 -> m.copy(voiceBegin = decodeVoiceBegin(message()))
                    31 -> m.copy(voicePayload = decodeVoicePayload(message()))
                    32 -> m.copy(voiceEnd = VoiceEnd(singleInt(message(), 1)))
                    40 -> m.copy(start = RemoteStart(singleInt(message(), 1) != 0))
                    50 -> m.copy(setVolumeLevel = decodeVolume(message()))
                    90 -> m.copy(appLinkLaunch = decodeAppLink(message()))
                    else -> { skip(); m }
                }
            }
            return m
        }

        private fun ProtoWriter.deviceInfo(d: DeviceInfo) {
            stringIfSet(1, d.model)
            stringIfSet(2, d.vendor)
            intIfSet(3, d.unknown1)
            stringIfSet(4, d.unknown2)
            stringIfSet(5, d.packageName)
            stringIfSet(6, d.appVersion)
        }

        private fun ProtoWriter.textFieldStatus(s: TextFieldStatus) {
            intIfSet(1, s.counterField)
            stringIfSet(2, s.value)
            intIfSet(3, s.start)
            intIfSet(4, s.end)
            intIfSet(5, s.int5)
            stringIfSet(6, s.label)
        }

        /** Reads a message whose only interesting field is the varint [wanted]. */
        private fun singleInt(r: ProtoReader, wanted: Int): Int {
            var value = 0
            r.forEachField { field -> if (field == wanted) value = int() else skip() }
            return value
        }

        private fun decodeConfigure(r: ProtoReader): RemoteConfigure {
            var code1 = 0
            var info: DeviceInfo? = null
            r.forEachField { field ->
                when (field) {
                    1 -> code1 = int()
                    2 -> info = decodeDeviceInfo(message())
                    else -> skip()
                }
            }
            return RemoteConfigure(code1, info)
        }

        private fun decodeDeviceInfo(r: ProtoReader): DeviceInfo {
            var d = DeviceInfo()
            r.forEachField { field ->
                d = when (field) {
                    1 -> d.copy(model = string())
                    2 -> d.copy(vendor = string())
                    3 -> d.copy(unknown1 = int())
                    4 -> d.copy(unknown2 = string())
                    5 -> d.copy(packageName = string())
                    6 -> d.copy(appVersion = string())
                    else -> { skip(); d }
                }
            }
            return d
        }

        private fun decodeError(r: ProtoReader): RemoteErrorMessage {
            var value = false
            // Field 2 echoes the offending RemoteMessage; it is skipped.
            r.forEachField { field -> if (field == 1) value = bool() else skip() }
            return RemoteErrorMessage(value)
        }

        private fun decodePingRequest(r: ProtoReader): PingRequest {
            var v1 = 0
            var v2 = 0
            r.forEachField { field ->
                when (field) {
                    1 -> v1 = int()
                    2 -> v2 = int()
                    else -> skip()
                }
            }
            return PingRequest(v1, v2)
        }

        private fun decodeKeyInject(r: ProtoReader): KeyInject {
            var code = 0
            var direction = 0
            r.forEachField { field ->
                when (field) {
                    1 -> code = int()
                    2 -> direction = int()
                    else -> skip()
                }
            }
            return KeyInject(code, direction)
        }

        private fun decodeTextFieldStatus(r: ProtoReader): TextFieldStatus {
            var s = TextFieldStatus()
            r.forEachField { field ->
                s = when (field) {
                    1 -> s.copy(counterField = int())
                    2 -> s.copy(value = string())
                    3 -> s.copy(start = int())
                    4 -> s.copy(end = int())
                    5 -> s.copy(int5 = int())
                    6 -> s.copy(label = string())
                    else -> { skip(); s }
                }
            }
            return s
        }

        private fun decodeImeKeyInject(r: ProtoReader): ImeKeyInject {
            var app: AppInfo? = null
            var status: TextFieldStatus? = null
            r.forEachField { field ->
                when (field) {
                    1 -> app = decodeAppInfo(message())
                    2 -> status = decodeTextFieldStatus(message())
                    else -> skip()
                }
            }
            return ImeKeyInject(app, status)
        }

        private fun decodeAppInfo(r: ProtoReader): AppInfo {
            var a = AppInfo()
            r.forEachField { field ->
                a = when (field) {
                    1 -> a.copy(counter = int())
                    10 -> a.copy(label = string())
                    12 -> a.copy(appPackage = string())
                    else -> { skip(); a }
                }
            }
            return a
        }

        private fun decodeBatchEdit(r: ProtoReader): ImeBatchEdit {
            var ime = 0
            var fieldCounter = 0
            val edits = mutableListOf<EditInfo>()
            r.forEachField { field ->
                when (field) {
                    1 -> ime = int()
                    2 -> fieldCounter = int()
                    3 -> edits += decodeEditInfo(message())
                    else -> skip()
                }
            }
            return ImeBatchEdit(ime, fieldCounter, edits)
        }

        private fun decodeEditInfo(r: ProtoReader): EditInfo {
            var insert = 0
            var obj: ImeObject? = null
            r.forEachField { field ->
                when (field) {
                    1 -> insert = int()
                    2 -> obj = decodeImeObject(message())
                    else -> skip()
                }
            }
            return EditInfo(insert, obj)
        }

        private fun decodeImeObject(r: ProtoReader): ImeObject {
            var o = ImeObject()
            r.forEachField { field ->
                o = when (field) {
                    1 -> o.copy(start = int())
                    2 -> o.copy(end = int())
                    3 -> o.copy(value = string())
                    else -> { skip(); o }
                }
            }
            return o
        }

        private fun decodeShowRequest(r: ProtoReader): ImeShowRequest {
            var status: TextFieldStatus? = null
            r.forEachField { field -> if (field == 2) status = decodeTextFieldStatus(message()) else skip() }
            return ImeShowRequest(status)
        }

        private fun decodeVoiceBegin(r: ProtoReader): VoiceBegin {
            var id = 0
            var pkg = ""
            r.forEachField { field ->
                when (field) {
                    1 -> id = int()
                    2 -> pkg = string()
                    else -> skip()
                }
            }
            return VoiceBegin(id, pkg)
        }

        private fun decodeVoicePayload(r: ProtoReader): VoicePayload {
            var id = 0
            var samples = ByteArray(0)
            r.forEachField { field ->
                when (field) {
                    1 -> id = int()
                    2 -> samples = bytes()
                    else -> skip()
                }
            }
            return VoicePayload(id, samples)
        }

        private fun decodeVolume(r: ProtoReader): SetVolumeLevel {
            var v = SetVolumeLevel()
            r.forEachField { field ->
                v = when (field) {
                    3 -> v.copy(playerModel = string())
                    6 -> v.copy(volumeMax = int())
                    7 -> v.copy(volumeLevel = int())
                    8 -> v.copy(volumeMuted = bool())
                    else -> { skip(); v }
                }
            }
            return v
        }

        private fun decodeAppLink(r: ProtoReader): AppLinkLaunchRequest {
            var link = ""
            r.forEachField { field -> if (field == 1) link = string() else skip() }
            return AppLinkLaunchRequest(link)
        }
    }
}
