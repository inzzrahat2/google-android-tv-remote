package com.techport.tvremote.ui.touchpad

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteKey
import kotlin.math.abs

private enum class GestureStart { TAP, DRAG, LONG_PRESS, CANCEL }

/**
 * Touchpad gesture loop:
 * - tap → DPAD_CENTER CLICK (sent on release, since a touch-down could still become a drag);
 * - hold without moving → DPAD_CENTER DOWN, then UP on release, so the TV applies its own
 *   long-press (e.g. a context menu);
 * - drag → D-pad steps from [stepper], one CLICK per step, with a throttled haptic tick.
 *
 * A double tap is two taps, i.e. two OK presses; the protocol has no separate double-click.
 * Only the first finger is tracked; extra fingers are ignored.
 */
internal suspend fun PointerInputScope.detectTouchpadGestures(
    stepper: GestureStepper,
    throttle: TickThrottle,
    onTouchingChange: (Boolean) -> Unit,
    onFeedback: () -> Unit,
    onKey: (RemoteKey, KeyAction) -> Unit,
) {
    val slopSquared = viewConfiguration.touchSlop * viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        stepper.reset()
        throttle.reset()
        onTouchingChange(true)
        try {
            var dragFrom = down.position
            val start = withTimeoutOrNull<GestureStart>(longPressMs) {
                while (true) {
                    val change = awaitTracked(down) ?: return@withTimeoutOrNull GestureStart.CANCEL
                    if (change.changedToUpIgnoreConsumed()) {
                        change.consume()
                        return@withTimeoutOrNull GestureStart.TAP
                    }
                    if ((change.position - down.position).getDistanceSquared() > slopSquared) {
                        dragFrom = change.position
                        change.consume()
                        return@withTimeoutOrNull GestureStart.DRAG
                    }
                    change.consume()
                }
                @Suppress("UNREACHABLE_CODE")
                GestureStart.CANCEL
            } ?: GestureStart.LONG_PRESS

            when (start) {
                GestureStart.TAP -> {
                    onFeedback()
                    onKey(RemoteKey.DPAD_CENTER, KeyAction.CLICK)
                }
                GestureStart.LONG_PRESS -> {
                    onFeedback()
                    onKey(RemoteKey.DPAD_CENTER, KeyAction.DOWN)
                    try {
                        waitForUpOrCancellation()?.consume()
                    } finally {
                        onKey(RemoteKey.DPAD_CENTER, KeyAction.UP)
                    }
                }
                GestureStart.DRAG -> {
                    // The travel spent crossing the touch slop counts toward the first step.
                    val initial = dragFrom - down.position
                    emitSteps(stepper.feed(initial.x, initial.y, 0L), down.uptimeMillis, stepper, throttle, onFeedback, onKey)
                    while (true) {
                        val change = awaitTracked(down) ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val delta: Offset = change.position - change.previousPosition
                        val steps = stepper.feed(delta.x, delta.y, change.uptimeMillis - change.previousUptimeMillis)
                        emitSteps(steps, change.uptimeMillis, stepper, throttle, onFeedback, onKey)
                        change.consume()
                    }
                }
                GestureStart.CANCEL -> Unit
            }
        } finally {
            onTouchingChange(false)
        }
    }
}

/** Next event for the pointer that started the gesture, or null if it disappeared. */
private suspend fun AwaitPointerEventScope.awaitTracked(down: PointerInputChange): PointerInputChange? {
    val changes = awaitPointerEvent().changes
    for (i in changes.indices) {
        if (changes[i].id == down.id) return changes[i]
    }
    return null
}

private inline fun emitSteps(
    steps: Int,
    nowMs: Long,
    stepper: GestureStepper,
    throttle: TickThrottle,
    onFeedback: () -> Unit,
    onKey: (RemoteKey, KeyAction) -> Unit,
) {
    val key = stepper.keyFor(steps) ?: return
    if (throttle.tryTick(nowMs)) onFeedback()
    repeat(abs(steps)) { onKey(key, KeyAction.CLICK) }
}
