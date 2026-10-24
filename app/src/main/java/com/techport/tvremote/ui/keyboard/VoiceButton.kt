package com.techport.tvremote.ui.keyboard

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techport.tvremote.R
import com.techport.tvremote.ui.components.LocalPressFeedback

/** A press shorter than this is a tap (hands-free: tap again to send); longer is hold-to-talk. */
private const val HOLD_THRESHOLD_MS = 400L

/**
 * Microphone button: hold to talk (press = start, release = send), or tap to start and tap again
 * to send. Asks for RECORD_AUDIO on first use, after a one-line rationale.
 *
 * The caller renders this only when the connected TV's `RemoteCapabilities.voice` is true;
 * if the TV still refuses a voice session, the user is told "Voice isn't supported by this TV".
 * Leaving the screen or backgrounding the app ends the utterance and releases the microphone.
 */
@Composable
fun VoiceButton(
    viewModel: VoiceViewModel,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showLabel: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val feedback = LocalPressFeedback.current
    var showRationale by remember { mutableStateOf(false) }
    var askedBefore by rememberSaveable { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Granted: the next press starts listening. We don't start on our own; the user's
        // finger has long left the button by now.
        if (!granted) viewModel.onPermissionDenied()
    }
    val hasPermission = {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val requestPermission by rememberUpdatedState {
        if (askedBefore) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            showRationale = true
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }

    val messages = rememberVoiceMessages()
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        viewModel.messageShown()
        Toast.makeText(context, messages.getValue(m), Toast.LENGTH_LONG).show()
    }

    val active = state.phase != VoicePhase.IDLE
    val pulse = rememberInfiniteTransition(label = "voice")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse",
    )
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.keyboard_voice_description)
    val status = when (state.phase) {
        VoicePhase.IDLE -> stringResource(R.string.keyboard_voice_hold)
        VoicePhase.STARTING -> stringResource(R.string.keyboard_voice_starting)
        VoicePhase.LISTENING -> stringResource(
            if (state.handsFree) R.string.keyboard_voice_listening_tap else R.string.keyboard_voice_listening,
        )
        VoicePhase.FINISHING -> stringResource(R.string.keyboard_voice_finishing)
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = CircleShape,
            color = if (active) colors.primary else colors.surfaceContainerHigh,
            contentColor = if (active) colors.onPrimary else colors.onSurface,
            modifier = Modifier
                .size(size)
                .scale(if (state.phase == VoicePhase.LISTENING) pulseScale else 1f)
                .semantics {
                    contentDescription = description
                    stateDescription = status
                    role = Role.Button
                    // Accessibility services can't hold; a click toggles hands-free listening.
                    onClick {
                        if (!hasPermission()) {
                            requestPermission()
                        } else {
                            viewModel.onPressStart()
                            viewModel.onPressEnd(held = false)
                        }
                        true
                    }
                }
                .pointerInput(viewModel) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        feedback()
                        if (!hasPermission()) {
                            waitForUpOrCancellation()?.consume()
                            requestPermission()
                            return@awaitEachGesture
                        }
                        viewModel.onPressStart()
                        val up = waitForUpOrCancellation()
                        up?.consume()
                        // A cancelled gesture (finger slid away) counts as a hold: send what we have.
                        val held = up == null || up.uptimeMillis - down.uptimeMillis >= HOLD_THRESHOLD_MS
                        viewModel.onPressEnd(held)
                    }
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (state.phase) {
                    VoicePhase.STARTING, VoicePhase.FINISHING ->
                        CircularProgressIndicator(Modifier.size(size * 0.45f), strokeWidth = 2.dp, color = colors.onPrimary)
                    VoicePhase.LISTENING -> Icon(Icons.Rounded.Mic, contentDescription = null)
                    VoicePhase.IDLE -> Icon(Icons.Rounded.MicNone, contentDescription = null)
                }
            }
        }
        if (showLabel || active) {
            Text(status, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.keyboard_voice_rationale_title)) },
            text = { Text(stringResource(R.string.keyboard_voice_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    askedBefore = true
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(stringResource(R.string.keyboard_voice_rationale_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text(stringResource(R.string.keyboard_voice_rationale_cancel))
                }
            },
        )
    }
}

@Composable
private fun rememberVoiceMessages(): Map<VoiceMessage, String> {
    val unsupported = stringResource(R.string.keyboard_voice_unsupported)
    val noPermission = stringResource(R.string.keyboard_voice_no_permission)
    val busy = stringResource(R.string.keyboard_voice_mic_unavailable)
    val micFailed = stringResource(R.string.keyboard_voice_mic_failed)
    val failed = stringResource(R.string.keyboard_voice_failed)
    return remember(unsupported, noPermission, busy, micFailed, failed) {
        mapOf(
            VoiceMessage.UNSUPPORTED to unsupported,
            VoiceMessage.NO_PERMISSION to noPermission,
            VoiceMessage.MIC_UNAVAILABLE to busy,
            VoiceMessage.MIC_FAILED to micFailed,
            VoiceMessage.FAILED to failed,
        )
    }
}
