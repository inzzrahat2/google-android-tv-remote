package com.techport.tvremote.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.techport.tvremote.domain.model.HapticStrength

/** Short, subtle click feedback for remote buttons. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val effects: Map<HapticStrength, VibrationEffect> = buildMap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(HapticStrength.LIGHT, VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            put(HapticStrength.MEDIUM, VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            put(HapticStrength.STRONG, VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            put(HapticStrength.LIGHT, VibrationEffect.createOneShot(8, 80))
            put(HapticStrength.MEDIUM, VibrationEffect.createOneShot(14, 160))
            put(HapticStrength.STRONG, VibrationEffect.createOneShot(22, 255))
        }
    }

    fun click(strength: HapticStrength) {
        val effect = effects[strength] ?: return
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(effect)
    }
}
