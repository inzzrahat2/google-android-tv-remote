package com.techport.tvremote.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    // Remote
    val haptics: HapticStrength = HapticStrength.LIGHT,
    /** Interval between repeats while a D-pad/volume key is held. */
    val keyRepeatIntervalMs: Long = 110,
    val confirmPower: Boolean = true,
    val gestureNavigation: Boolean = true,
    /** Touchpad swipe sensitivity multiplier, 0.5..2.0. */
    val touchpadSensitivity: Float = 1f,
    val remoteLayout: RemoteLayout = RemoteLayout.STANDARD,
    /** Buttons the user chose to hide from the remote screen. */
    val hiddenKeys: Set<RemoteKey> = emptySet(),
    /** Optional quick bar on the remote screen. Empty = hidden. */
    val quickActions: List<QuickAction> = emptyList(),

    // Appearance
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,

    // Connection
    val autoReconnect: Boolean = true,
    /** Covers TCP + TLS + a possible NSD re-resolve of a TV whose address changed. */
    val connectionTimeoutSec: Int = 12,

    // Keyboard
    /** Open the keyboard automatically when the TV focuses a text field. */
    val autoOpenKeyboard: Boolean = true,
    /** Send ENTER after text is submitted. */
    val sendOnEnter: Boolean = true,
    /** Opt-in: allow "Paste to TV" to read the phone clipboard. */
    val clipboardEnabled: Boolean = false,

    // Onboarding
    val onboardingDone: Boolean = false,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

@Serializable
enum class HapticStrength { OFF, LIGHT, MEDIUM, STRONG }

@Serializable
enum class RemoteLayout { STANDARD, MINIMAL, FULL }
