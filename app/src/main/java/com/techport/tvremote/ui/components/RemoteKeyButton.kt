package com.techport.tvremote.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteKey

/**
 * A physical-feeling remote button. Visual feedback is immediate and local; it never
 * waits for the network.
 */
@Composable
fun RemoteKeyButton(
    key: RemoteKey,
    label: String,
    onKey: (RemoteKey, KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    repeatIntervalMs: Long = 110,
    shape: Shape = RoundedCornerShape(20.dp),
    minSize: Dp = 56.dp,
    emphasized: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(70), label = "press")
    val feedback = LocalPressFeedback.current
    val colors = MaterialTheme.colorScheme
    val container = when {
        pressed -> colors.primaryContainer
        emphasized -> colors.secondaryContainer
        else -> colors.surfaceContainerHigh
    }

    Surface(
        shape = shape,
        color = container,
        contentColor = if (pressed) colors.onPrimaryContainer else colors.onSurface,
        modifier = modifier
            .defaultMinSize(minSize, minSize)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                contentDescription = label
                role = Role.Button
                if (!enabled) disabled()
                onClick(label) {
                    onKey(key, KeyAction.CLICK)
                    true
                }
            }
            .pointerInput(key, enabled, repeatIntervalMs) {
                if (!enabled) return@pointerInput
                detectKeyPresses(
                    repeatIntervalMs = repeatIntervalMs,
                    keyAt = { key },
                    onPressedChange = { pressed = it != null },
                    onFeedback = feedback,
                    onKey = onKey,
                )
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null)
            } else {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
