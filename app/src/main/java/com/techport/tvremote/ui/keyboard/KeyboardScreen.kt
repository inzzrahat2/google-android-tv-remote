package com.techport.tvremote.ui.keyboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techport.tvremote.R
import com.techport.tvremote.ui.components.KEY_REPEAT_INITIAL_DELAY_MS
import com.techport.tvremote.ui.components.LocalPressFeedback

/**
 * Full-screen keyboard: a text field driven by the phone's own IME, mirrored live onto the TV,
 * with a toolbar for the keys an IME doesn't have (arrows, Esc, Enter, Paste to TV, Clear).
 *
 * @param onOpenSettings shown as the snackbar action when Paste to TV is turned off; null hides it.
 * @param voiceButton optional slot in the top bar, e.g. `{ VoiceButton(voiceVm) }`; pass it
 *   only when the connected TV has the voice capability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    onClose: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    voiceButton: (@Composable () -> Unit)? = null,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val focus = remember { FocusRequester() }
    val ime = LocalSoftwareKeyboardController.current

    val close = {
        viewModel.flush()
        onClose()
    }
    BackHandler(onBack = close)

    // Autofocus and raise the phone keyboard as soon as typing is possible.
    LaunchedEffect(ui.enabled) {
        if (ui.enabled) {
            runCatching { focus.requestFocus() }
            ime?.show()
        }
    }

    val texts = rememberMessageTexts()
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        viewModel.messageShown()
        val withSettings = m == KeyboardMessage.CLIPBOARD_DISABLED && onOpenSettings != null
        val result = snackbar.showSnackbar(
            message = texts.of(m),
            actionLabel = if (withSettings) texts.openSettings else null,
            duration = if (withSettings) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onOpenSettings?.invoke()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.keyboard_close))
                    }
                },
                title = {
                    Text(
                        ui.fieldLabel ?: stringResource(R.string.keyboard_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = { if (voiceButton != null && ui.enabled) voiceButton() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!ui.enabled) UnavailableCard(ui.availability)

            OutlinedTextField(
                value = input,
                onValueChange = viewModel::onValueChange,
                enabled = ui.enabled,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text(stringResource(R.string.keyboard_placeholder)) },
                supportingText = if (ui.enabled) {
                    { Text(stringResource(R.string.keyboard_field_hint)) }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.titleLarge,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = true,
                    keyboardType = KeyboardType.Text,
                    imeAction = if (ui.sendOnEnter) ImeAction.Send else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { viewModel.onImeAction() },
                    onDone = {
                        if (!viewModel.onImeAction()) ime?.hide()
                    },
                ),
            )

            Spacer(Modifier.weight(1f))

            KeyboardToolbar(ui, viewModel)
        }
    }
}

@Composable
private fun KeyboardToolbar(ui: KeyboardUiState, viewModel: KeyboardViewModel) {
    val enabled = ui.enabled
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToolKey(
                icon = Icons.Rounded.ChevronLeft,
                label = stringResource(R.string.keyboard_cursor_left),
                enabled = ui.canMoveCursor,
                repeats = true,
                onPress = { viewModel.moveCursor(right = false) },
                modifier = Modifier.weight(1f),
            )
            ToolKey(
                icon = Icons.Rounded.SpaceBar,
                label = stringResource(R.string.keyboard_space),
                enabled = enabled,
                onPress = viewModel::space,
                modifier = Modifier.weight(2f),
            )
            ToolKey(
                icon = Icons.Rounded.ChevronRight,
                label = stringResource(R.string.keyboard_cursor_right),
                enabled = ui.canMoveCursor,
                repeats = true,
                onPress = { viewModel.moveCursor(right = true) },
                modifier = Modifier.weight(1f),
            )
            ToolKey(
                icon = Icons.AutoMirrored.Rounded.Backspace,
                label = stringResource(R.string.keyboard_backspace),
                enabled = enabled,
                repeats = true,
                onPress = viewModel::backspace,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToolKey(
                text = stringResource(R.string.keyboard_escape),
                label = stringResource(R.string.keyboard_escape_description),
                enabled = enabled,
                onPress = viewModel::escape,
                modifier = Modifier.weight(1f),
            )
            ToolKey(
                icon = Icons.Rounded.ClearAll,
                label = stringResource(R.string.keyboard_clear),
                enabled = enabled,
                onPress = viewModel::clear,
                modifier = Modifier.weight(1f),
            )
            ToolKey(
                icon = Icons.Rounded.ContentPaste,
                label = stringResource(R.string.keyboard_paste),
                enabled = enabled,
                onPress = viewModel::pasteFromClipboard,
                modifier = Modifier.weight(1f),
                // Dimmed (still tappable) while opted out, so the tap can explain how to turn it on.
                dimmed = !ui.clipboardEnabled,
            )
            ToolKey(
                icon = if (ui.sendOnEnter) Icons.AutoMirrored.Rounded.Send else Icons.AutoMirrored.Rounded.KeyboardReturn,
                label = stringResource(if (ui.sendOnEnter) R.string.keyboard_send else R.string.keyboard_enter),
                enabled = enabled,
                onPress = viewModel::submit,
                modifier = Modifier.weight(1.5f),
                emphasized = true,
                showLabel = true,
            )
        }
    }
}

@Composable
private fun UnavailableCard(availability: KeyboardAvailability) {
    val (title, body) = when (availability) {
        KeyboardAvailability.UNSUPPORTED -> R.string.keyboard_unsupported_title to R.string.keyboard_unsupported_body
        else -> R.string.keyboard_not_connected_title to R.string.keyboard_not_connected_body
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A toolbar key that acts on touch-down (like the remote's keys). Keys with [repeats] repeat
 * while held, so Backspace and the arrows behave like a real keyboard.
 */
@Composable
private fun ToolKey(
    label: String,
    enabled: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    repeats: Boolean = false,
    emphasized: Boolean = false,
    dimmed: Boolean = false,
    showLabel: Boolean = false,
) {
    val feedback = LocalPressFeedback.current
    val currentOnPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val container = when {
        pressed -> colors.primaryContainer
        emphasized -> colors.primary
        else -> colors.surfaceContainerHigh
    }
    val content = when {
        pressed -> colors.onPrimaryContainer
        emphasized -> colors.onPrimary
        else -> colors.onSurface
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .alpha(if (!enabled) 0.38f else if (dimmed) 0.6f else 1f)
            .semantics {
                contentDescription = label
                role = Role.Button
                if (!enabled) disabled()
                onClick(label) {
                    currentOnPress()
                    true
                }
            }
            .pointerInput(enabled, repeats) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    feedback()
                    currentOnPress()
                    try {
                        if (repeats) {
                            var wait = KEY_REPEAT_INITIAL_DELAY_MS
                            while (withTimeoutOrNull(wait) { waitForUpOrCancellation() ?: Unit } == null) {
                                currentOnPress()
                                wait = REPEAT_INTERVAL_MS
                            }
                        } else {
                            waitForUpOrCancellation()?.consume()
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                if (text != null || showLabel) {
                    Text(text ?: label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

private const val REPEAT_INTERVAL_MS = 70L

/** Message texts resolved during composition, so the snackbar effect needn't touch resources. */
private class KeyboardMessageTexts(
    private val disabled: String,
    private val empty: String,
    private val truncated: String,
    val openSettings: String,
) {
    fun of(message: KeyboardMessage) = when (message) {
        KeyboardMessage.CLIPBOARD_DISABLED -> disabled
        KeyboardMessage.CLIPBOARD_EMPTY -> empty
        KeyboardMessage.CLIPBOARD_TRUNCATED -> truncated
    }
}

@Composable
private fun rememberMessageTexts() = KeyboardMessageTexts(
    disabled = stringResource(R.string.keyboard_clipboard_disabled),
    empty = stringResource(R.string.keyboard_clipboard_empty),
    truncated = stringResource(R.string.keyboard_clipboard_truncated),
    openSettings = stringResource(R.string.keyboard_open_settings),
)
