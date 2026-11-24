package com.techport.tvremote.ui.apps

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techport.tvremote.R
import com.techport.tvremote.data.apps.AppLinkSuggestion
import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.ui.components.LocalPressFeedback
import com.techport.tvremote.ui.components.SectionHeader

/**
 * The app launcher. The protocol cannot list installed apps, so this manages user-saved app
 * links; tapping one sends it to the TV. Management stays usable while disconnected.
 */
@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var deleting by remember { mutableStateOf<AppShortcut?>(null) }
    val sections = remember(state.shortcuts, query) { buildAppsSections(state.shortcuts, query) }
    val feedback = LocalPressFeedback.current
    val canLaunch = state.availability == LaunchAvailability.READY
    val searching = query.isNotBlank()

    val actions = TileActions(
        canLaunch = canLaunch,
        displayOrder = state.shortcuts,
        onLaunch = { if (viewModel.launch(it)) feedback() },
        onFavorite = viewModel::setFavorite,
        onEdit = { editing = EditTarget(it) },
        onMove = viewModel::move,
        onDelete = { deleting = it },
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        fullWidth("header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    stringResource(R.string.apps_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                FilledTonalIconButton(onClick = { editing = EditTarget(null) }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.apps_add))
                }
            }
        }
        if (state.loaded && !canLaunch) {
            fullWidth("availability") { AvailabilityBanner(state.availability) }
        }
        if (state.shortcuts.isNotEmpty()) {
            fullWidth("search") { SearchField(query, onQueryChange = { query = it }) }
        }
        if (state.loaded && state.shortcuts.isEmpty()) {
            fullWidth("empty") { EmptyState() }
        }

        section("fav", R.string.apps_section_favorites, sections.favorites, actions, movable = true)
        section("recent", R.string.apps_section_recent, sections.recent, actions, movable = false)
        section(
            "all",
            if (searching) R.string.apps_section_results else R.string.apps_section_all,
            sections.all,
            actions,
            movable = !searching,
        )
        if (searching && sections.all.isEmpty()) {
            fullWidth("no-results") {
                Text(
                    stringResource(R.string.apps_no_results, query.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!searching && state.suggestions.isNotEmpty()) {
            fullWidth("suggestions-header") {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader(R.string.apps_suggestions_title)
                    Text(
                        stringResource(R.string.apps_suggestions_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.suggestions, key = { "suggestion-${it.link}" }) { suggestion ->
                SuggestionTile(suggestion, onAdd = { viewModel.addSuggestion(suggestion) })
            }
        }
    }

    editing?.let { target ->
        ShortcutDialog(
            initial = target.shortcut,
            onSave = { label, link ->
                viewModel.save(target.shortcut?.id, label, link).also { if (it == null) editing = null }
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.apps_delete_title, shortcut.label)) },
            text = { Text(stringResource(R.string.apps_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(shortcut)
                    deleting = null
                }) { Text(stringResource(R.string.apps_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Null [shortcut] = adding a new one. */
private data class EditTarget(val shortcut: AppShortcut?)

private class TileActions(
    val canLaunch: Boolean,
    val displayOrder: List<AppShortcut>,
    val onLaunch: (AppShortcut) -> Unit,
    val onFavorite: (AppShortcut, Boolean) -> Unit,
    val onEdit: (AppShortcut) -> Unit,
    val onMove: (AppShortcut, Int) -> Unit,
    val onDelete: (AppShortcut) -> Unit,
)

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.section(
    keyPrefix: String,
    @StringRes title: Int,
    shortcuts: List<AppShortcut>,
    actions: TileActions,
    movable: Boolean,
) {
    if (shortcuts.isEmpty()) return
    fullWidth("$keyPrefix-header") {
        Box(Modifier.padding(top = 12.dp)) { SectionHeader(title) }
    }
    items(shortcuts, key = { "$keyPrefix-${it.id}" }) { shortcut ->
        AppTile(shortcut, actions, movable)
    }
}

@Composable
private fun AvailabilityBanner(availability: LaunchAvailability) {
    val text = when (availability) {
        LaunchAvailability.UNSUPPORTED -> R.string.apps_unavailable_unsupported
        else -> R.string.apps_unavailable_not_connected
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.apps_search)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                androidx.compose.material3.IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.apps_search_clear))
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyState() {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.apps_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.apps_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppTile(shortcut: AppShortcut, actions: TileActions, movable: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    val optionsLabel = stringResource(R.string.apps_options, shortcut.label)
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClickLabel = shortcut.label,
                    onLongClickLabel = optionsLabel,
                    // While the TV can't launch apps, a tap opens the menu so management stays reachable.
                    onClick = { if (actions.canLaunch) actions.onLaunch(shortcut) else menuOpen = true },
                    onLongClick = { menuOpen = true },
                )
                .padding(vertical = 8.dp, horizontal = 4.dp),
        ) {
            Monogram(shortcut.label, isFavorite = shortcut.isFavorite, dimmed = !actions.canLaunch)
            Text(
                shortcut.label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem(
                if (shortcut.isFavorite) R.string.action_unfavorite else R.string.action_favorite,
                if (shortcut.isFavorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
            ) {
                menuOpen = false
                actions.onFavorite(shortcut, !shortcut.isFavorite)
            }
            MenuItem(R.string.apps_edit, Icons.Rounded.Edit) {
                menuOpen = false
                actions.onEdit(shortcut)
            }
            if (movable && canMove(actions.displayOrder, shortcut.id, -1)) {
                MenuItem(R.string.action_move_up, Icons.Rounded.ArrowUpward) {
                    menuOpen = false
                    actions.onMove(shortcut, -1)
                }
            }
            if (movable && canMove(actions.displayOrder, shortcut.id, 1)) {
                MenuItem(R.string.action_move_down, Icons.Rounded.ArrowDownward) {
                    menuOpen = false
                    actions.onMove(shortcut, 1)
                }
            }
            MenuItem(R.string.apps_delete, Icons.Rounded.Delete) {
                menuOpen = false
                actions.onDelete(shortcut)
            }
        }
    }
}

@Composable
private fun MenuItem(@StringRes label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun SuggestionTile(suggestion: AppLinkSuggestion, onAdd: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClickLabel = stringResource(R.string.apps_suggestion_add, suggestion.label),
                onClick = onAdd,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Box {
            Monogram(suggestion.label, isFavorite = false, dimmed = true)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            suggestion.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A letter tile instead of an icon: icons would need network fetching, which the app avoids. */
@Composable
private fun Monogram(label: String, isFavorite: Boolean, dimmed: Boolean) {
    val colors = MaterialTheme.colorScheme
    val palette: List<Pair<Color, Color>> = listOf(
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
        colors.surfaceContainerHighest to colors.onSurface,
    )
    val (container, content) = palette[monogramColorIndex(label, palette.size)]
    Box(
        Modifier
            .size(64.dp)
            .alpha(if (dimmed) 0.6f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(monogram(label), style = MaterialTheme.typography.titleLarge, color = content)
        if (isFavorite) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = stringResource(R.string.apps_favorite_badge),
                tint = content,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp),
            )
        }
    }
}
