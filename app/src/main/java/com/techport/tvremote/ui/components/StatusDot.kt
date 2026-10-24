package com.techport.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.ConnectionState

@Composable
fun StatusDot(state: ConnectionState, modifier: Modifier = Modifier) {
    val color = when (state) {
        is ConnectionState.Connected -> Color(0xFF4CC38A)
        is ConnectionState.Connecting, is ConnectionState.Reconnecting -> Color(0xFFF2B84B)
        is ConnectionState.PairingRequired, is ConnectionState.Disconnected -> MaterialTheme.colorScheme.error
        ConnectionState.Idle -> MaterialTheme.colorScheme.outline
    }
    Box(modifier.size(10.dp).background(color, CircleShape))
}

fun ConnectionState.labelRes(): Int = when (this) {
    ConnectionState.Idle -> R.string.state_idle
    is ConnectionState.Connecting -> R.string.state_connecting
    is ConnectionState.Connected -> R.string.state_connected
    is ConnectionState.Reconnecting -> R.string.state_reconnecting
    is ConnectionState.PairingRequired -> R.string.state_pairing_required
    is ConnectionState.Disconnected -> R.string.state_disconnected
}
