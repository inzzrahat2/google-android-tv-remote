package com.techport.tvremote.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techport.tvremote.core.logging.LogLevel
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.device
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Debug builds only. Developer-facing, so text is intentionally not localized. */
@Composable
fun DebugScreen(viewModel: DebugViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val latency by viewModel.latency.collectAsStateWithLifecycle()
    val time = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    LaunchedEffect(state is ConnectionState.Connected) { viewModel.measure() }

    val caps = (state as? ConnectionState.Connected)?.capabilities ?: RemoteCapabilities.NONE
    val lines = buildList {
        add("Device" to (state.device?.let { "${it.name} (${it.host}:${it.port})" } ?: "-"))
        add("Protocol" to (state.device?.protocolId ?: "-"))
        add("Connection" to state::class.simpleName.orEmpty())
        add("Latency" to (latency?.let { "$it ms" } ?: "-"))
        add("Connect time" to (metrics.connectTimeMs?.let { "$it ms" } ?: "-"))
        add("Commands sent" to metrics.commandsSent.toString())
        add("Last command" to (metrics.lastCommand ?: "-"))
        add("Last send time" to (metrics.lastCommandSendMs?.let { "$it ms" } ?: "-"))
        add("Last error" to (metrics.lastError?.name ?: "-"))
        add(
            "Capabilities" to listOf(
                "D-pad" to caps.dpad, "Keyboard" to caps.keyboard, "Volume" to caps.volume, "Mute" to caps.mute,
                "Power" to caps.power, "Media" to caps.mediaControls, "Apps" to caps.appLaunch, "Voice" to caps.voice,
                "Inputs" to caps.inputSwitching,
            ).joinToString("  ") { (name, on) -> "$name ${if (on) "✓" else "✗"}" },
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(lines) { (label, value) ->
            Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                OutlinedButton(onClick = viewModel::reconnect) { Text("Reconnect") }
                OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
                OutlinedButton(onClick = viewModel::sendTest) { Text("Send OK") }
                OutlinedButton(onClick = viewModel::measure) { Text("Ping") }
                OutlinedButton(onClick = viewModel::clearLogs) { Text("Clear logs") }
            }
        }
        items(logs.asReversed()) { entry ->
            Text(
                "${time.format(Date(entry.timeMillis))} ${entry.level.name.first()} ${entry.tag}: ${entry.message}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = when (entry.level) {
                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
