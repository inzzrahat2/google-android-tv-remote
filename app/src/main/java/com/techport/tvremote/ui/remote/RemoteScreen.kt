package com.techport.tvremote.ui.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.RemoteLayout
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.domain.model.device
import com.techport.tvremote.ui.components.DPad
import com.techport.tvremote.ui.components.RemoteKeyButton
import com.techport.tvremote.ui.components.StatusDot
import com.techport.tvremote.ui.components.labelRes
import com.techport.tvremote.ui.components.text

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    onOpenDevices: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val saved by viewModel.savedDevices.collectAsStateWithLifecycle()
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    var confirmPower by remember { mutableStateOf(false) }

    val device = state.device
    if (device == null) {
        NoTvState(onOpenDevices)
        return
    }

    val connected = state as? ConnectionState.Connected
    // Before the first connection we don't know capabilities; show the core controls disabled
    // rather than an empty screen, so the layout doesn't jump when the session comes up.
    val caps = connected?.capabilities ?: PLACEHOLDER_CAPABILITIES
    val enabled = connected != null

    val onKey: (RemoteKey, KeyAction) -> Unit = { key, action ->
        if (key == RemoteKey.POWER && settings.confirmPower) {
            if (action != KeyAction.UP) confirmPower = true
        } else {
            viewModel.onKey(key, action)
        }
    }
    val controls = RemoteControls(caps, enabled, settings.keyRepeatIntervalMs, settings.remoteLayout, settings.hiddenKeys, volume, onKey)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
        Column(Modifier.fillMaxSize()) {
            DeviceHeader(
                device = device,
                state = state,
                showPower = caps.power && RemoteKey.POWER !in settings.hiddenKeys,
                powerEnabled = enabled,
                onSwitch = { switcherOpen = true },
                onPower = { onKey(RemoteKey.POWER, KeyAction.CLICK) },
            )
            ConnectionBanner(state, onRetry = viewModel::retry, onChangeTv = onOpenDevices)
            if (landscape) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { controls.DPadArea(Modifier.widthIn(max = 300.dp)) }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        controls.VolumeRow()
                        controls.NavigationRow()
                        controls.MediaRow()
                    }
                }
            } else {
                // One-handed: controls sit toward the bottom, D-pad in the thumb zone.
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Bottom),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    controls.VolumeRow()
                    controls.DPadArea(Modifier.fillMaxWidth(0.82f).widthIn(max = 320.dp))
                    controls.NavigationRow()
                    controls.MediaRow()
                }
            }
        }
    }

    if (switcherOpen) {
        DeviceSwitcher(
            devices = saved,
            current = device,
            onSelect = {
                switcherOpen = false
                if (it.id != device.id) viewModel.switchTo(it)
            },
            onManage = {
                switcherOpen = false
                onOpenDevices()
            },
            onDismiss = { switcherOpen = false },
        )
    }

    if (confirmPower) {
        AlertDialog(
            onDismissRequest = { confirmPower = false },
            title = { Text(stringResource(R.string.remote_power_confirm_title)) },
            text = { Text(stringResource(R.string.remote_power_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmPower = false
                    viewModel.onKey(RemoteKey.POWER, KeyAction.CLICK)
                }) { Text(stringResource(R.string.remote_power_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPower = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private val PLACEHOLDER_CAPABILITIES = RemoteCapabilities(dpad = true, navigation = true, volume = true, mute = true)

/** Groups the control rows so portrait and landscape share one implementation. */
private class RemoteControls(
    val caps: RemoteCapabilities,
    val enabled: Boolean,
    val repeatMs: Long,
    val layout: RemoteLayout,
    val hidden: Set<RemoteKey>,
    val volume: VolumeState?,
    val onKey: (RemoteKey, KeyAction) -> Unit,
) {
    @Composable
    fun DPadArea(modifier: Modifier) {
        if (caps.dpad) DPad(onKey = onKey, enabled = enabled, repeatIntervalMs = repeatMs, modifier = modifier)
    }

    @Composable
    fun VolumeRow() {
        if (!caps.volume && !caps.mute) return
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (caps.volume) {
                Key(RemoteKey.VOLUME_DOWN, R.string.key_volume_down, Icons.AutoMirrored.Rounded.VolumeDown, Modifier.weight(1f))
            }
            if (caps.mute && RemoteKey.VOLUME_MUTE !in hidden) {
                val muted = volume?.muted == true
                RemoteKeyButton(
                    key = RemoteKey.VOLUME_MUTE,
                    label = stringResource(R.string.key_mute),
                    icon = Icons.AutoMirrored.Rounded.VolumeOff,
                    onKey = onKey,
                    enabled = enabled,
                    emphasized = muted,
                    modifier = Modifier.weight(1f).height(56.dp),
                )
            }
            if (caps.volume) {
                Key(RemoteKey.VOLUME_UP, R.string.key_volume_up, Icons.AutoMirrored.Rounded.VolumeUp, Modifier.weight(1f))
            }
        }
        if (caps.volumeLevel && volume != null) {
            Text(
                if (volume.muted) {
                    stringResource(R.string.remote_muted)
                } else {
                    stringResource(R.string.remote_volume_percent, volume.percent)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    fun NavigationRow() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (caps.navigation) {
                Key(RemoteKey.BACK, R.string.key_back, Icons.AutoMirrored.Rounded.ArrowBack, Modifier.weight(1f))
                Key(RemoteKey.HOME, R.string.key_home, Icons.Rounded.Home, Modifier.weight(1f))
            }
            if (caps.systemKeys && layout != RemoteLayout.MINIMAL) {
                if (RemoteKey.APP_SWITCH !in hidden) {
                    Key(RemoteKey.APP_SWITCH, R.string.key_recents, Icons.Rounded.ViewCarousel, Modifier.weight(1f))
                }
                if (RemoteKey.MENU !in hidden) Key(RemoteKey.MENU, R.string.key_menu, Icons.Rounded.Menu, Modifier.weight(1f))
            }
        }
        val showChannels = caps.channels && RemoteKey.CHANNEL_UP !in hidden
        val showInput = caps.inputSwitching && RemoteKey.TV_INPUT !in hidden
        if (layout == RemoteLayout.FULL && (showChannels || showInput)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showChannels) {
                    Key(RemoteKey.CHANNEL_DOWN, R.string.key_channel_down, Icons.Rounded.KeyboardArrowDown, Modifier.weight(1f))
                    Key(RemoteKey.CHANNEL_UP, R.string.key_channel_up, Icons.Rounded.KeyboardArrowUp, Modifier.weight(1f))
                }
                if (showInput) {
                    Key(RemoteKey.TV_INPUT, R.string.key_input, Icons.Rounded.Input, Modifier.weight(1f))
                }
            }
        }
    }

    @Composable
    fun MediaRow() {
        if (!caps.mediaControls || layout == RemoteLayout.MINIMAL || RemoteKey.MEDIA_PLAY_PAUSE in hidden) return
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (layout == RemoteLayout.FULL) {
                Key(RemoteKey.MEDIA_REWIND, R.string.key_rewind, Icons.Rounded.FastRewind, Modifier.weight(1f))
            }
            Key(RemoteKey.MEDIA_PREVIOUS, R.string.key_previous, Icons.Rounded.SkipPrevious, Modifier.weight(1f))
            Key(RemoteKey.MEDIA_PLAY_PAUSE, R.string.key_play_pause, Icons.Rounded.PlayArrow, Modifier.weight(1.3f), emphasized = true)
            Key(RemoteKey.MEDIA_NEXT, R.string.key_next, Icons.Rounded.SkipNext, Modifier.weight(1f))
            if (layout == RemoteLayout.FULL) {
                Key(RemoteKey.MEDIA_FAST_FORWARD, R.string.key_fast_forward, Icons.Rounded.FastForward, Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun Key(
        key: RemoteKey,
        labelRes: Int,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        modifier: Modifier,
        emphasized: Boolean = false,
    ) {
        RemoteKeyButton(
            key = key,
            label = stringResource(labelRes),
            icon = icon,
            onKey = onKey,
            enabled = enabled,
            repeatIntervalMs = repeatMs,
            emphasized = emphasized,
            modifier = modifier.height(56.dp),
        )
    }
}

@Composable
private fun DeviceHeader(
    device: TvDevice,
    state: ConnectionState,
    showPower: Boolean,
    powerEnabled: Boolean,
    onSwitch: () -> Unit,
    onPower: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.remote_switch_tv), onClick = onSwitch)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(state)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(state.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
        }
        if (showPower) {
            RemoteKeyButton(
                key = RemoteKey.POWER,
                label = stringResource(R.string.key_power),
                icon = Icons.Rounded.PowerSettingsNew,
                onKey = { _, action -> if (action != KeyAction.UP) onPower() },
                enabled = powerEnabled,
                shape = CircleShape,
                minSize = 48.dp,
            )
        }
    }
}

@Composable
private fun ConnectionBanner(state: ConnectionState, onRetry: () -> Unit, onChangeTv: () -> Unit) {
    when (state) {
        is ConnectionState.Connecting -> ProgressBanner(stringResource(R.string.state_connecting))
        is ConnectionState.Reconnecting ->
            ProgressBanner(stringResource(R.string.remote_reconnecting_attempt, state.attempt))
        is ConnectionState.Disconnected -> state.error?.let { error ->
            ErrorBanner(error.text().title, error.text().body, R.string.action_try_again, onRetry, onChangeTv)
        } ?: ErrorBanner(R.string.state_disconnected, null, R.string.action_reconnect, onRetry, onChangeTv)
        is ConnectionState.PairingRequired -> {
            val text = state.reason?.text()
            ErrorBanner(
                text?.title ?: R.string.state_pairing_required,
                text?.body ?: R.string.error_pairing_body,
                R.string.action_pair,
                onChangeTv,
                onChangeTv,
            )
        }
        else -> Unit
    }
}

@Composable
private fun ProgressBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorBanner(title: Int, body: Int?, action: Int, onAction: () -> Unit, onChangeTv: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Button(onClick = onAction) { Text(stringResource(action)) }
                OutlinedButton(onClick = onChangeTv) { Text(stringResource(R.string.action_change_tv)) }
            }
        }
    }
}

@Composable
private fun NoTvState(onFind: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(Icons.Rounded.Tv, contentDescription = null, modifier = Modifier.padding(24.dp).size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.remote_no_tv_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.remote_no_tv_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFind) { Text(stringResource(R.string.action_find_tv)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSwitcher(
    devices: List<TvDevice>,
    current: TvDevice,
    onSelect: (TvDevice) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.remote_switch_tv),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        devices.forEach { device ->
            ListItem(
                headlineContent = { Text(device.name) },
                supportingContent = device.model?.let { { Text(it) } },
                leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) },
                trailingContent = if (device.id == current.id) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
                modifier = Modifier.clickable { onSelect(device) },
            )
        }
        TextButton(onClick = onManage, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.devices_title))
        }
        Spacer(Modifier.height(16.dp))
    }
}
