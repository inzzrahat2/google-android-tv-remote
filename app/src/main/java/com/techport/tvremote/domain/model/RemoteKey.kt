package com.techport.tvremote.domain.model

/**
 * Protocol-independent remote keys.
 *
 * [keyCode] uses the android.view.KeyEvent code space because it is a stable, publicly
 * documented vocabulary; protocol adapters translate it to whatever they put on the wire.
 */
enum class RemoteKey(val keyCode: Int, val group: KeyGroup) {
    DPAD_UP(19, KeyGroup.DPAD),
    DPAD_DOWN(20, KeyGroup.DPAD),
    DPAD_LEFT(21, KeyGroup.DPAD),
    DPAD_RIGHT(22, KeyGroup.DPAD),
    DPAD_CENTER(23, KeyGroup.DPAD),

    HOME(3, KeyGroup.NAVIGATION),
    BACK(4, KeyGroup.NAVIGATION),
    MENU(82, KeyGroup.SYSTEM),
    APP_SWITCH(187, KeyGroup.SYSTEM),
    GUIDE(172, KeyGroup.SYSTEM),
    SETTINGS(176, KeyGroup.SYSTEM),
    ASSIST(219, KeyGroup.SYSTEM),
    SEARCH(84, KeyGroup.SYSTEM),
    INFO(165, KeyGroup.SYSTEM),
    CAPTIONS(175, KeyGroup.SYSTEM),

    VOLUME_UP(24, KeyGroup.VOLUME),
    VOLUME_DOWN(25, KeyGroup.VOLUME),
    VOLUME_MUTE(164, KeyGroup.MUTE),

    POWER(26, KeyGroup.POWER),

    MEDIA_PLAY_PAUSE(85, KeyGroup.MEDIA),
    MEDIA_PLAY(126, KeyGroup.MEDIA),
    MEDIA_PAUSE(127, KeyGroup.MEDIA),
    MEDIA_STOP(86, KeyGroup.MEDIA),
    MEDIA_NEXT(87, KeyGroup.MEDIA),
    MEDIA_PREVIOUS(88, KeyGroup.MEDIA),
    MEDIA_REWIND(89, KeyGroup.MEDIA),
    MEDIA_FAST_FORWARD(90, KeyGroup.MEDIA),

    CHANNEL_UP(166, KeyGroup.CHANNEL),
    CHANNEL_DOWN(167, KeyGroup.CHANNEL),

    TV_INPUT(178, KeyGroup.INPUT),
    TV_INPUT_HDMI_1(243, KeyGroup.INPUT),
    TV_INPUT_HDMI_2(244, KeyGroup.INPUT),
    TV_INPUT_HDMI_3(245, KeyGroup.INPUT),
    TV_INPUT_HDMI_4(246, KeyGroup.INPUT),

    ENTER(66, KeyGroup.TEXT),
    DEL(67, KeyGroup.TEXT),
    FORWARD_DEL(112, KeyGroup.TEXT),
    ESCAPE(111, KeyGroup.TEXT),
    ;

    /** Keys that repeat while held, like a physical remote's arrows and volume rocker. */
    val repeatsWhenHeld: Boolean
        get() = group == KeyGroup.VOLUME || this in DIRECTIONS

    companion object {
        val DIRECTIONS = setOf(DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT)
    }
}

enum class KeyGroup { DPAD, NAVIGATION, SYSTEM, VOLUME, MUTE, POWER, MEDIA, CHANNEL, INPUT, TEXT }

/**
 * How a key is delivered.
 *
 * [CLICK] is a complete press. [DOWN]/[UP] mirror a physical button so the TV can apply
 * its own long-press behaviour (e.g. holding OK opens a context menu).
 */
enum class KeyAction { CLICK, DOWN, UP }
