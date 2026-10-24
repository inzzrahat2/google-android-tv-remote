package com.techport.tvremote.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputScope
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteKey

/** Fired once per physical press (not on repeats). */
val LocalPressFeedback = staticCompositionLocalOf<() -> Unit> { {} }

/** Delay before a held repeating key starts repeating. */
const val KEY_REPEAT_INITIAL_DELAY_MS = 380L

/**
 * Press handling shared by every remote control. The key goes out on touch-down, not on
 * release, so the TV reacts as early as possible.
 *
 * - Repeating keys (arrows, volume): CLICK on down, then CLICK every [repeatIntervalMs] while held.
 * - Other keys: DOWN on touch, UP on release, so the TV applies its own long-press behaviour.
 */
suspend fun PointerInputScope.detectKeyPresses(
    repeatIntervalMs: Long,
    keyAt: (Offset) -> RemoteKey?,
    onPressedChange: (RemoteKey?) -> Unit,
    onFeedback: () -> Unit,
    onKey: (RemoteKey, KeyAction) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val key = keyAt(down.position) ?: return@awaitEachGesture
        down.consume()
        onPressedChange(key)
        onFeedback()
        try {
            if (key.repeatsWhenHeld) {
                onKey(key, KeyAction.CLICK)
                if (!awaitRelease(KEY_REPEAT_INITIAL_DELAY_MS)) {
                    do {
                        onKey(key, KeyAction.CLICK)
                    } while (!awaitRelease(repeatIntervalMs))
                }
            } else {
                onKey(key, KeyAction.DOWN)
                waitForUpOrCancellation()?.consume()
                onKey(key, KeyAction.UP)
            }
        } finally {
            onPressedChange(null)
        }
    }
}

/** True once the pointer is released or the gesture is cancelled; false if [timeoutMs] passed first. */
private suspend fun AwaitPointerEventScope.awaitRelease(timeoutMs: Long): Boolean =
    withTimeoutOrNull(timeoutMs) {
        waitForUpOrCancellation()?.consume()
        true
    } ?: false
