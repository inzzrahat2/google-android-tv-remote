package com.techport.tvremote.ui.touchpad

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.ui.components.LocalPressFeedback
import com.techport.tvremote.ui.components.RemoteKeyButton

/**
 * A large gesture area that drives the TV's D-pad: drag to move focus, tap for OK, hold for a
 * long OK press. The protocol has no pointer, so this moves focus, not a cursor.
 *
 * [sensitivity] is [com.techport.tvremote.domain.model.AppSettings.touchpadSensitivity] (0.5..2).
 */
@Composable
fun TouchpadSurface(
    onKey: (RemoteKey, KeyAction) -> Unit,
    sensitivity: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val currentOnKey by rememberUpdatedState(onKey)
    val feedback by rememberUpdatedState(LocalPressFeedback.current)
    var touching by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        if (touching) colors.primary else colors.outlineVariant,
        tween(120),
        label = "touchpadBorder",
    )

    val description = stringResource(R.string.touchpad_label)
    val actionLabels = listOf(
        RemoteKey.DPAD_UP to stringResource(R.string.key_up),
        RemoteKey.DPAD_DOWN to stringResource(R.string.key_down),
        RemoteKey.DPAD_LEFT to stringResource(R.string.key_left),
        RemoteKey.DPAD_RIGHT to stringResource(R.string.key_right),
    )
    val okLabel = stringResource(R.string.key_ok)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (touching) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                contentDescription = description
                if (!enabled) {
                    disabled()
                } else {
                    onClick(okLabel) {
                        currentOnKey(RemoteKey.DPAD_CENTER, KeyAction.CLICK)
                        true
                    }
                    customActions = actionLabels.map { (key, label) ->
                        CustomAccessibilityAction(label) {
                            currentOnKey(key, KeyAction.CLICK)
                            true
                        }
                    }
                }
            }
            .pointerInput(enabled, sensitivity, density) {
                if (!enabled) return@pointerInput
                // One stepper per gesture detector; reset at every touch-down, so nothing is allocated per event.
                val stepper = GestureStepper.forDensity(density, sensitivity)
                detectTouchpadGestures(
                    stepper = stepper,
                    throttle = TickThrottle(),
                    onTouchingChange = { touching = it },
                    onFeedback = { feedback() },
                    onKey = { key, action -> currentOnKey(key, action) },
                )
            },
    ) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    stringResource(if (enabled) R.string.touchpad_hint else R.string.touchpad_disabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Touchpad mode: the gesture area plus the Back / Home / OK row from a physical remote.
 * Place it where the remote screen's D-pad would be (e.g. as an alternate layout toggle).
 */
@Composable
fun TouchpadScreen(
    onKey: (RemoteKey, KeyAction) -> Unit,
    sensitivity: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TouchpadSurface(
            onKey = onKey,
            sensitivity = sensitivity,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showNavigation) {
                RemoteKeyButton(
                    key = RemoteKey.BACK,
                    label = stringResource(R.string.key_back),
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onKey = onKey,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(56.dp),
                )
                RemoteKeyButton(
                    key = RemoteKey.HOME,
                    label = stringResource(R.string.key_home),
                    icon = Icons.Rounded.Home,
                    onKey = onKey,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(56.dp),
                )
            }
            RemoteKeyButton(
                key = RemoteKey.DPAD_CENTER,
                label = stringResource(R.string.key_ok),
                onKey = onKey,
                enabled = enabled,
                emphasized = true,
                modifier = Modifier.weight(1f).height(56.dp),
            )
        }
    }
}
