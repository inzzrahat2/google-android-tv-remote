package com.techport.tvremote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteKey
import kotlin.math.atan2
import kotlin.math.hypot

private const val INNER_RATIO = 0.36f

/**
 * Circular D-pad. One pointer handler hit-tests the four arcs and the centre, so the
 * whole disc is touch target and there is no gap between buttons.
 */
@Composable
fun DPad(
    onKey: (RemoteKey, KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    repeatIntervalMs: Long = 110,
) {
    var pressed by remember { mutableStateOf<RemoteKey?>(null) }
    val feedback = LocalPressFeedback.current
    val colors = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .alpha(if (enabled) 1f else 0.38f)
            .pointerInput(enabled, repeatIntervalMs) {
                if (!enabled) return@pointerInput
                detectKeyPresses(
                    repeatIntervalMs = repeatIntervalMs,
                    keyAt = { hitTest(it, Size(size.width.toFloat(), size.height.toFloat())) },
                    onPressedChange = { pressed = it },
                    onFeedback = feedback,
                    onKey = onKey,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val diameter = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(colors.surfaceContainerHigh, radius, center)
            pressed?.takeIf { it != RemoteKey.DPAD_CENTER }?.let { key ->
                val start = when (key) {
                    RemoteKey.DPAD_RIGHT -> -45f
                    RemoteKey.DPAD_DOWN -> 45f
                    RemoteKey.DPAD_LEFT -> 135f
                    else -> 225f
                }
                val ring = radius * (1 - INNER_RATIO)
                drawArc(
                    color = colors.primaryContainer,
                    startAngle = start,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius + ring / 2, center.y - radius + ring / 2),
                    size = Size((radius - ring / 2) * 2, (radius - ring / 2) * 2),
                    style = Stroke(width = ring),
                )
            }
            drawCircle(colors.outlineVariant, radius, center, style = Stroke(1.dp.toPx()))
            drawCircle(
                if (pressed == RemoteKey.DPAD_CENTER) colors.primary else colors.surfaceContainerHighest,
                radius * INNER_RATIO,
                center,
            )
        }

        val arrowOffset = diameter * 0.34f
        Arrow(0f, Modifier.offset(y = -arrowOffset), pressed == RemoteKey.DPAD_UP)
        Arrow(180f, Modifier.offset(y = arrowOffset), pressed == RemoteKey.DPAD_DOWN)
        Arrow(270f, Modifier.offset(x = -arrowOffset), pressed == RemoteKey.DPAD_LEFT)
        Arrow(90f, Modifier.offset(x = arrowOffset), pressed == RemoteKey.DPAD_RIGHT)
        Text(
            stringResource(R.string.key_ok),
            style = MaterialTheme.typography.titleMedium,
            color = if (pressed == RemoteKey.DPAD_CENTER) colors.onPrimary else colors.onSurface,
        )

        // Invisible per-key nodes so TalkBack can focus and activate each direction.
        val third = diameter / 3
        AccessibleKey(RemoteKey.DPAD_UP, R.string.key_up, third, Modifier.offset(y = -third), onKey)
        AccessibleKey(RemoteKey.DPAD_DOWN, R.string.key_down, third, Modifier.offset(y = third), onKey)
        AccessibleKey(RemoteKey.DPAD_LEFT, R.string.key_left, third, Modifier.offset(x = -third), onKey)
        AccessibleKey(RemoteKey.DPAD_RIGHT, R.string.key_right, third, Modifier.offset(x = third), onKey)
        AccessibleKey(RemoteKey.DPAD_CENTER, R.string.key_ok, third, Modifier, onKey)
    }
}

@Composable
private fun Arrow(rotation: Float, modifier: Modifier, pressed: Boolean) {
    val colors = MaterialTheme.colorScheme
    Icon(
        Icons.Rounded.KeyboardArrowUp,
        contentDescription = null,
        tint = if (pressed) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = modifier.size(36.dp).rotate(rotation),
    )
}

@Composable
private fun AccessibleKey(
    key: RemoteKey,
    labelRes: Int,
    size: Dp,
    modifier: Modifier,
    onKey: (RemoteKey, KeyAction) -> Unit,
) {
    val label = stringResource(labelRes)
    Box(
        modifier
            .size(size)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label) {
                    onKey(key, KeyAction.CLICK)
                    true
                }
            },
    )
}

/** Maps a touch point to a D-pad key, or null outside the disc. */
internal fun hitTest(point: Offset, size: Size): RemoteKey? {
    val radius = size.minDimension / 2
    val dx = point.x - size.width / 2
    val dy = point.y - size.height / 2
    val distance = hypot(dx, dy)
    if (distance > radius) return null
    if (distance <= radius * INNER_RATIO) return RemoteKey.DPAD_CENTER
    val degrees = Math.toDegrees(atan2(dy, dx).toDouble())
    return when {
        degrees >= -45 && degrees < 45 -> RemoteKey.DPAD_RIGHT
        degrees >= 45 && degrees < 135 -> RemoteKey.DPAD_DOWN
        degrees >= -135 && degrees < -45 -> RemoteKey.DPAD_UP
        else -> RemoteKey.DPAD_LEFT
    }
}
