package com.techport.tvremote.ui.inputs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.ui.components.LocalPressFeedback
import com.techport.tvremote.ui.quickactions.quickKeyIcon
import com.techport.tvremote.ui.quickactions.quickKeyLabel

/** Direct HDMI inputs, in order. */
val HDMI_INPUT_KEYS = listOf(
    RemoteKey.TV_INPUT_HDMI_1,
    RemoteKey.TV_INPUT_HDMI_2,
    RemoteKey.TV_INPUT_HDMI_3,
    RemoteKey.TV_INPUT_HDMI_4,
)

/**
 * Input-source picker. Show it only when `capabilities.inputSwitching` is true. The protocol
 * can't read which inputs exist or which is active, so every option is a plain key press and
 * the sheet says that some TVs ignore them.
 *
 * "Next input" leaves the sheet open (users often press it several times); picking an HDMI
 * input closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputsSheet(
    onKey: (RemoteKey, KeyAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val feedback = LocalPressFeedback.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.inputs_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.inputs_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            InputRow(
                key = RemoteKey.TV_INPUT,
                label = stringResource(R.string.inputs_cycle),
                supporting = stringResource(R.string.inputs_cycle_body),
            ) {
                feedback()
                onKey(RemoteKey.TV_INPUT, KeyAction.CLICK)
            }
            HDMI_INPUT_KEYS.forEach { key ->
                InputRow(key = key, label = quickKeyLabel(key), supporting = null) {
                    feedback()
                    onKey(key, KeyAction.CLICK)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun InputRow(key: RemoteKey, label: String, supporting: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = quickKeyIcon(key)?.let { { Icon(it, contentDescription = null) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}
