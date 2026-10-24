package com.techport.tvremote.ui.devices

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techport.tvremote.R
import com.techport.tvremote.core.permissions.LocalNetworkPermission
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.DiscoveredDevice
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.device
import com.techport.tvremote.ui.components.StatusDot
import com.techport.tvremote.ui.components.labelRes

@Composable
fun DevicesScreen(viewModel: DevicesViewModel, onOpenRemote: () -> Unit) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var renaming by remember { mutableStateOf<TvDevice?>(null) }
    var forgetting by remember { mutableStateOf<TvDevice?>(null) }
    var permissionGranted by remember { mutableStateOf(LocalNetworkPermission.isGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    val latencyUnavailable = stringResource(R.string.devices_latency_unavailable)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DevicesEvent.OpenRemote -> onOpenRemote()
                is DevicesEvent.Latency -> snackbar.showSnackbar(
                    event.millis?.let { context.getString(R.string.devices_latency, it.toInt()) } ?: latencyUnavailable,
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item { SectionTitle(R.string.devices_title) }

            if (viewModel.needsLocalNetwork && !permissionGranted) {
                item {
                    PermissionCard(onAllow = { permissionLauncher.launch(LocalNetworkPermission.PERMISSION) })
                }
            }

            if (saved.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.devices_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            items(saved, key = { it.id }) { device ->
                SavedDeviceRow(
                    device = device,
                    connection = connection.takeIf { it.device?.id == device.id },
                    online = device.id in nearby.onlineIds,
                    isFirst = device == saved.first(),
                    isLast = device == saved.last(),
                    onConnect = {
                        if (connection is ConnectionState.PairingRequired && connection.device?.id == device.id) {
                            viewModel.pairAgain(device)
                        } else {
                            viewModel.connect(device)
                        }
                    },
                    onDisconnect = viewModel::disconnect,
                    onRename = { renaming = device },
                    onForget = { forgetting = device },
                    onFavorite = { viewModel.toggleFavorite(device) },
                    onMove = { viewModel.move(device, it) },
                    onTest = viewModel::testConnection,
                )
            }

            item { SectionTitle(R.string.devices_nearby) }
            items(nearby.devices, key = { "nearby-" + it.id }) { device ->
                NearbyDeviceRow(device, onPair = { viewModel.pair(device) })
            }
            item {
                if (nearby.searching) {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.devices_searching), style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (nearby.devices.isEmpty()) {
                    Text(
                        stringResource(R.string.devices_none_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    pairing?.let { PairingDialog(it, onSubmit = viewModel::submitCode, onRetry = { viewModel.pair(it.target) }, onDismiss = viewModel::cancelPairing) }

    renaming?.let { device ->
        RenameDialog(device, onDismiss = { renaming = null }, onSave = {
            viewModel.rename(device, it)
            renaming = null
        })
    }

    forgetting?.let { device ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text(stringResource(R.string.devices_forget_title, device.name)) },
            text = { Text(stringResource(R.string.devices_forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.forget(device)
                    forgetting = null
                }) { Text(stringResource(R.string.action_forget)) }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun PermissionCard(onAllow: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.error_permission_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.error_permission_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAllow) { Text(stringResource(R.string.action_allow)) }
        }
    }
}

@Composable
private fun SavedDeviceRow(
    device: TvDevice,
    connection: ConnectionState?,
    online: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRename: () -> Unit,
    onForget: () -> Unit,
    onFavorite: () -> Unit,
    onMove: (Int) -> Unit,
    onTest: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val connected = connection is ConnectionState.Connected
    val status = when {
        connection != null -> stringResource(connection.labelRes())
        online -> stringResource(R.string.state_available)
        device.lastConnectedAt != null -> stringResource(
            R.string.devices_last_used,
            DateUtils.getRelativeTimeSpanString(device.lastConnectedAt).toString().lowercase(),
        )
        else -> stringResource(R.string.state_offline)
    }

    ListItem(
        headlineContent = { Text(device.name) },
        supportingContent = { Text(status) },
        leadingContent = {
            Box {
                Icon(Icons.Rounded.Tv, contentDescription = null)
                if (connection != null) StatusDot(connection, Modifier.align(Alignment.BottomEnd))
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (device.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(if (device.isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                        tint = if (device.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        fun item(label: Int, action: () -> Unit): @Composable () -> Unit = {
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                menuOpen = false
                                action()
                            })
                        }
                        if (connected) {
                            item(R.string.action_test_connection, onTest)()
                            item(R.string.action_disconnect, onDisconnect)()
                        } else {
                            item(R.string.action_connect, onConnect)()
                        }
                        item(R.string.action_rename, onRename)()
                        if (!isFirst) item(R.string.action_move_up) { onMove(-1) }()
                        if (!isLast) item(R.string.action_move_down) { onMove(1) }()
                        item(R.string.action_forget, onForget)()
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onConnect),
    )
}

@Composable
private fun NearbyDeviceRow(device: DiscoveredDevice, onPair: () -> Unit) {
    ListItem(
        headlineContent = { Text(device.name) },
        supportingContent = { Text(listOfNotNull(device.model, device.host).joinToString(" · ")) },
        leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) },
        trailingContent = { Button(onClick = onPair) { Text(stringResource(R.string.action_pair)) } },
        modifier = Modifier.clickable(onClick = onPair),
    )
}

@Composable
private fun RenameDialog(device: TvDevice, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(device.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.devices_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text(stringResource(R.string.devices_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
