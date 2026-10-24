package com.techport.tvremote.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.HapticStrength
import com.techport.tvremote.domain.model.RemoteLayout
import com.techport.tvremote.domain.model.ThemeMode
import kotlin.math.roundToLong

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, showDebug: Boolean, onOpenDebug: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
                    Slider(
                        value = (MAX_REPEAT_MS + MIN_REPEAT_MS - settings.keyRepeatIntervalMs).toFloat(),
                        onValueChange = { v ->
                            val interval = MAX_REPEAT_MS + MIN_REPEAT_MS - v.roundToLong()
                            viewModel.update { it.copy(keyRepeatIntervalMs = interval) }
                        },
                        valueRange = MIN_REPEAT_MS.toFloat()..MAX_REPEAT_MS.toFloat(),
                    )
                }
            },
        )
        Toggle(R.string.settings_confirm_power, null, settings.confirmPower) { v -> viewModel.update { it.copy(confirmPower = v) } }
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
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
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
