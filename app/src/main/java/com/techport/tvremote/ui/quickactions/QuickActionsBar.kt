package com.techport.tvremote.ui.quickactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.model.KeyAction
import com.techport.tvremote.domain.model.QuickAction
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.ui.components.LocalPressFeedback

/**
 * A horizontally scrolling row of the user's quick actions. Renders nothing when there is
 * nothing to show.
 *
 * @param isKeySupported keys the connected TV can't receive are hidden (pass
 *   `capabilities::supports`).
 * @param appLaunchEnabled app chips are shown but disabled when the TV can't open app links.
 */
@Composable
fun QuickActionsBar(
    actions: List<QuickAction>,
    shortcuts: List<AppShortcut>,
    enabled: Boolean,
    onKey: (RemoteKey, KeyAction) -> Unit,
    onLaunch: (AppShortcut) -> Unit,
    modifier: Modifier = Modifier,
    isKeySupported: (RemoteKey) -> Boolean = { true },
    appLaunchEnabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val resolved = remember(actions, shortcuts) { resolveQuickActions(actions, shortcuts) }
    val visible = resolved.filter { it !is ResolvedQuickAction.Key || isKeySupported(it.key) }
    if (visible.isEmpty()) return
    val feedback = LocalPressFeedback.current

    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.forEach { action ->
            when (action) {
                is ResolvedQuickAction.Key -> QuickChip(
                    label = quickKeyLabel(action.key),
                    icon = quickKeyIcon(action.key),
                    enabled = enabled,
                ) {
                    feedback()
                    onKey(action.key, KeyAction.CLICK)
                }
                is ResolvedQuickAction.App -> QuickChip(
                    label = action.shortcut.label,
                    icon = QuickAppIcon,
                    enabled = enabled && appLaunchEnabled,
                ) {
                    feedback()
                    onLaunch(action.shortcut)
                }
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, icon: ImageVector?, enabled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) } },
    )
}
