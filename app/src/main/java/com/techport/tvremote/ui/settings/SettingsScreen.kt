package com.techport.tvremote.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.HapticStrength
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.RemoteLayout
import com.techport.tvremote.domain.model.ThemeMode
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    showDebug: Boolean,
    onOpenDebug: () -> Unit,
    /** Supplied by the caller once the quick-actions editor is available; null hides the entry. */
    quickActionsEditor: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var customizing by rememberSaveable { mutableStateOf(false) }
    var editingQuickActions by rememberSaveable { mutableStateOf(false) }

    if (customizing) {
        CustomizeButtonsDialog(
            hidden = settings.hiddenKeys,
            onToggle = { key, visible ->
                viewModel.update { it.copy(hiddenKeys = if (visible) it.hiddenKeys - key else it.hiddenKeys + key) }
            },
            onDismiss = { customizing = false },
        )
    }
    if (editingQuickActions) quickActionsEditor?.invoke { editingQuickActions = false }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Section(R.string.settings_section_remote)
        Choice(
            title = R.string.settings_haptics,
            options = HapticStrength.entries,
            selected = settings.haptics,
            label = {
                when (it) {
                    HapticStrength.OFF -> R.string.settings_haptics_off
                    HapticStrength.LIGHT -> R.string.settings_haptics_light
                    HapticStrength.MEDIUM -> R.string.settings_haptics_medium
                    HapticStrength.STRONG -> R.string.settings_haptics_strong
                }
            },
            onSelect = { value -> viewModel.update { it.copy(haptics = value) } },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_key_repeat)) },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.settings_key_repeat_value, settings.keyRepeatIntervalMs.toInt()))
                    // Lower interval = faster repeat; the slider is inverted so right means faster.
                    CommitSlider(
                        label = R.string.settings_key_repeat,
                        value = (MAX_REPEAT_MS + MIN_REPEAT_MS - settings.keyRepeatIntervalMs).toFloat(),
                        onCommit = { v ->
                            val interval = MAX_REPEAT_MS + MIN_REPEAT_MS - v.roundToLong()
                            viewModel.update { it.copy(keyRepeatIntervalMs = interval) }
                        },
                        valueRange = MIN_REPEAT_MS.toFloat()..MAX_REPEAT_MS.toFloat(),
                    )
                }
            },
        )
        Toggle(R.string.settings_confirm_power, null, settings.confirmPower) { v -> viewModel.update { it.copy(confirmPower = v) } }
        Toggle(R.string.settings_gesture_nav, R.string.settings_gesture_nav_summary, settings.gestureNavigation) { v ->
            viewModel.update { it.copy(gestureNavigation = v) }
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_touchpad_sensitivity)) },
            supportingContent = {
                CommitSlider(
                    label = R.string.settings_touchpad_sensitivity,
                    value = settings.touchpadSensitivity,
                    onCommit = { v -> viewModel.update { it.copy(touchpadSensitivity = v) } },
                    valueRange = 0.5f..2f,
                )
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_customize_buttons)) },
            supportingContent = { Text(stringResource(R.string.settings_customize_buttons_summary)) },
            modifier = Modifier.clickable { customizing = true },
        )
        if (quickActionsEditor != null) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_quick_actions)) },
                supportingContent = { Text(stringResource(R.string.settings_quick_actions_summary)) },
                modifier = Modifier.clickable { editingQuickActions = true },
            )
        }
        Choice(
            title = R.string.settings_layout,
            options = RemoteLayout.entries,
            selected = settings.remoteLayout,
            label = {
                when (it) {
                    RemoteLayout.STANDARD -> R.string.settings_layout_standard
                    RemoteLayout.MINIMAL -> R.string.settings_layout_minimal
                    RemoteLayout.FULL -> R.string.settings_layout_full
                }
            },
            onSelect = { value -> viewModel.update { it.copy(remoteLayout = value) } },
        )

        Section(R.string.settings_section_appearance)
        Choice(
            title = R.string.settings_theme,
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = {
                when (it) {
                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                    ThemeMode.LIGHT -> R.string.settings_theme_light
                    ThemeMode.DARK -> R.string.settings_theme_dark
                    ThemeMode.AMOLED -> R.string.settings_theme_amoled
                }
            },
            onSelect = { value -> viewModel.update { it.copy(themeMode = value) } },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Toggle(R.string.settings_dynamic_color, R.string.settings_dynamic_color_summary, settings.dynamicColor) { v ->
                viewModel.update { it.copy(dynamicColor = v) }
            }
        }

        Section(R.string.settings_section_connection)
        Toggle(R.string.settings_auto_reconnect, R.string.settings_auto_reconnect_summary, settings.autoReconnect) { v ->
            viewModel.update { it.copy(autoReconnect = v) }
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_timeout)) },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.settings_timeout_value, settings.connectionTimeoutSec))
                    CommitSlider(
                        label = R.string.settings_timeout,
                        value = settings.connectionTimeoutSec.toFloat(),
                        onCommit = { v -> viewModel.update { it.copy(connectionTimeoutSec = v.roundToInt()) } },
                        // Floor of 10 s: a reconnect may need TCP + an NSD re-resolve + TLS.
                        valueRange = MIN_TIMEOUT_SEC.toFloat()..30f,
                        steps = 30 - MIN_TIMEOUT_SEC - 1,
                    )
                }
            },
        )

        Section(R.string.settings_section_keyboard)
        Toggle(R.string.settings_auto_open_keyboard, R.string.settings_auto_open_keyboard_summary, settings.autoOpenKeyboard) { v ->
            viewModel.update { it.copy(autoOpenKeyboard = v) }
        }
        Toggle(R.string.settings_send_on_enter, null, settings.sendOnEnter) { v -> viewModel.update { it.copy(sendOnEnter = v) } }
        Toggle(R.string.settings_clipboard, R.string.settings_clipboard_summary, settings.clipboardEnabled) { v ->
            viewModel.update { it.copy(clipboardEnabled = v) }
        }

        Section(R.string.settings_section_privacy)
        Text(
            stringResource(R.string.settings_privacy_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        if (showDebug) {
            Section(R.string.settings_section_developer)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_debug)) },
                modifier = Modifier.clickable(onClick = onOpenDebug),
            )
        }
    }
}

private const val MIN_TIMEOUT_SEC = 10
private const val MIN_REPEAT_MS = 60L
private const val MAX_REPEAT_MS = 250L

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Toggle(title: Int, summary: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row is the control, so TalkBack reads the title with the switch state.
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun <T> Choice(title: Int, options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(stringResource(label(option))) },
                    )
                }
            }
        },
    )
}

@Composable
private fun CustomizeButtonsDialog(
    hidden: Set<RemoteKey>,
    onToggle: (RemoteKey, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_customize_buttons)) },
        text = {
            Column {
                HIDEABLE_KEYS.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = key !in hidden, onValueChange = { onToggle(key, it) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = key !in hidden, onCheckedChange = null)
                        Text(stringResource(label), modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

/** Buttons a user may hide. Groups are represented by one key (see RemoteScreen). */
private val HIDEABLE_KEYS = listOf(
    RemoteKey.POWER to R.string.key_power,
    RemoteKey.VOLUME_MUTE to R.string.key_mute,
    RemoteKey.MEDIA_PLAY_PAUSE to R.string.settings_hide_media,
    RemoteKey.APP_SWITCH to R.string.key_recents,
    RemoteKey.MENU to R.string.key_menu,
    RemoteKey.CHANNEL_UP to R.string.settings_hide_channels,
    RemoteKey.TV_INPUT to R.string.key_input,
)

/** Tracks the drag locally and persists once on release, instead of a DataStore write per frame. */
@Composable
private fun CommitSlider(
    label: Int,
    value: Float,
    onCommit: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val labelText = stringResource(label)
    Slider(
        value = dragging ?: value,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let(onCommit)
            dragging = null
        },
        valueRange = valueRange,
        steps = steps,
        modifier = Modifier.semantics { contentDescription = labelText },
    )
}
