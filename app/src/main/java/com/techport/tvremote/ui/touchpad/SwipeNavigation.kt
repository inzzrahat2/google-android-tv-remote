package com.techport.tvremote.ui.touchpad

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.ui.components.LocalPressFeedback

/** Shortest swipe that counts as a direction. */
private val MIN_SWIPE_DISTANCE = 56.dp

/**
 * Swipe navigation for the remote screen: one swipe on an empty area sends one directional
 * D-pad CLICK, classified on release by [SwipeClassifier].
 *
 * It never steals input from controls:
 * - a gesture whose touch-down was consumed by a child (every remote button consumes its
 *   down) is ignored entirely;
 * - nothing is consumed until the finger moves past the touch slop, so taps pass through;
 * - if a child claims the drag first (e.g. a `verticalScroll` container inside this one), the
 *   gesture is abandoned.
 *
 * Limitation: because children see events first, vertical swipes inside a vertically
 * scrolling container are taken by the scroll. Apply this to a non-scrolling region, or to a
 * screen whose content fits without scrolling. Multi-finger gestures are ignored.
 */
fun Modifier.swipeNavigation(
    enabled: Boolean,
    onKey: (RemoteKey, KeyAction) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    composed {
        val currentOnKey by rememberUpdatedState(onKey)
        val feedback by rememberUpdatedState(LocalPressFeedback.current)
        pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            val minDistance = MIN_SWIPE_DISTANCE.toPx()
            awaitEachGesture {
                // requireUnconsumed: a button that took the touch-down keeps the whole gesture.
                val down = awaitFirstDown(requireUnconsumed = true)
                var dragging = false
                var endX = down.position.x
                var endY = down.position.y
                var endTime = down.uptimeMillis
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.size > 1) return@awaitEachGesture
                    val change = event.changes[0]
                    if (change.id != down.id || change.isConsumed) return@awaitEachGesture
                    endX = change.position.x
                    endY = change.position.y
                    endTime = change.uptimeMillis
                    if (!change.pressed) break
                    if (!dragging && (change.position - down.position).getDistance() > slop) dragging = true
                    if (dragging) change.consume()
                }
                if (!dragging) return@awaitEachGesture
                val key = SwipeClassifier.classify(
                    dx = endX - down.position.x,
                    dy = endY - down.position.y,
                    durationMs = endTime - down.uptimeMillis,
                    minDistancePx = minDistance,
                ) ?: return@awaitEachGesture
                feedback()
                currentOnKey(key, KeyAction.CLICK)
            }
        }
    }
}
