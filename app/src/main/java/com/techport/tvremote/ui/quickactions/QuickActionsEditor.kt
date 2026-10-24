package com.techport.tvremote.ui.quickactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.model.QuickAction

/**
 * Picks up to [MAX_QUICK_ACTIONS] keys and app shortcuts and orders them. [onSave] receives
 * a sanitized list (no duplicates, no deleted shortcuts); an empty list hides the bar.
 */
@Composable
fun QuickActionsEditor(
    current: List<QuickAction>,
    shortcuts: List<AppShortcut>,
    onSave: (List<QuickAction>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember(current, shortcuts) { mutableStateOf(sanitizeQuickActions(current, shortcuts)) }
    val byId = remember(shortcuts) { shortcuts.associateBy { it.id } }
    val full = selection.size >= MAX_QUICK_ACTIONS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quickactions_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(stringResource(R.string.quickactions_selected, selection.size, MAX_QUICK_ACTIONS))
                if (selection.isEmpty()) {
                    Text(
                        stringResource(R.string.quickactions_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                selection.forEachIndexed { index, action ->
                    val (label, icon) = when (action) {
                        is QuickAction.Key -> quickKeyLabel(action.key) to quickKeyIcon(action.key)
                        is QuickAction.App -> (byId[action.shortcutId]?.label.orEmpty()) to QuickAppIcon
                    }
                    SelectedRow(
                        label = label,
                        icon = icon,
                        canMoveUp = index > 0,
                        canMoveDown = index < selection.lastIndex,
                        onMove = { delta -> selection = selection.moved(index, delta) },
                        onRemove = { selection = selection - action },
                    )
                }

                SectionLabel(stringResource(R.string.quickactions_buttons))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_ACTION_KEYS.forEach { key ->
                        val action = QuickAction.Key(key)
                        PickChip(
                            label = quickKeyLabel(key),
                            icon = quickKeyIcon(key),
                            selected = action in selection,
                            enabled = action in selection || !full,
                        ) { selection = selection.toggled(action) }
                    }
                }

                SectionLabel(stringResource(R.string.quickactions_apps))
                if (shortcuts.isEmpty()) {
                    Text(
                        stringResource(R.string.quickactions_no_apps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shortcuts.forEach { shortcut ->
                            val action = QuickAction.App(shortcut.id)
                            PickChip(
                                label = shortcut.label,
                                icon = QuickAppIcon,
                                selected = action in selection,
                                enabled = action in selection || !full,
                            ) { selection = selection.toggled(action) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sanitizeQuickActions(selection, shortcuts)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun SelectedRow(
    label: String,
    icon: ImageVector?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.action_move_up))
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.action_move_down))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.quickactions_remove, label))
        }
    }
}

@Composable
private fun PickChip(label: String, icon: ImageVector?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } },
    )
}
