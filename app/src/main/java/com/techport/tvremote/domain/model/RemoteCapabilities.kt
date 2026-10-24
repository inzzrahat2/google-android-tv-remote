package com.techport.tvremote.domain.model

/**
 * What the connected device can actually do. The UI renders controls from this and
 * never assumes a feature exists.
 */
data class RemoteCapabilities(
    val dpad: Boolean = false,
    /** Home/Back. */
    val navigation: Boolean = false,
    /** Menu, Guide, Settings, Recents, Assistant: deliverable keys whose effect varies by TV. */
    val systemKeys: Boolean = false,
    val keyboard: Boolean = false,
    /** Gesture surface mapped to D-pad; available whenever [dpad] is. */
    val touchpad: Boolean = false,
    val volume: Boolean = false,
    /** The device reports its volume level, so a percentage can be shown. */
    val volumeLevel: Boolean = false,
    val mute: Boolean = false,
    val power: Boolean = false,
    val mediaControls: Boolean = false,
    val channels: Boolean = false,
    val appLaunch: Boolean = false,
    val voice: Boolean = false,
    val inputSwitching: Boolean = false,
) {
    fun supports(key: RemoteKey): Boolean = when (key.group) {
        KeyGroup.DPAD -> dpad
        KeyGroup.NAVIGATION -> navigation
        KeyGroup.SYSTEM -> systemKeys
        KeyGroup.VOLUME -> volume
        KeyGroup.MUTE -> mute
        KeyGroup.POWER -> power
        KeyGroup.MEDIA -> mediaControls
        KeyGroup.CHANNEL -> channels
        KeyGroup.INPUT -> inputSwitching
        KeyGroup.TEXT -> keyboard
    }

    companion object {
        val NONE = RemoteCapabilities()
    }
}

data class VolumeState(val level: Int, val max: Int, val muted: Boolean) {
    val percent: Int get() = if (max <= 0) 0 else (level * 100 / max).coerceIn(0, 100)
}
