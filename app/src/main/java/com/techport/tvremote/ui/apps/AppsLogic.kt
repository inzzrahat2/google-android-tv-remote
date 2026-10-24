package com.techport.tvremote.ui.apps

import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.model.ConnectionState
import java.net.URI

/** Whether an app link can be sent right now, and if not, why. */
enum class LaunchAvailability { READY, NOT_CONNECTED, UNSUPPORTED }

fun launchAvailability(state: ConnectionState): LaunchAvailability = when (state) {
    is ConnectionState.Connected ->
        if (state.capabilities.appLaunch) LaunchAvailability.READY else LaunchAvailability.UNSUPPORTED
    else -> LaunchAvailability.NOT_CONNECTED
}

data class AppsSections(
    val favorites: List<AppShortcut>,
    val recent: List<AppShortcut>,
    val all: List<AppShortcut>,
)

const val RECENT_LIMIT = 4

/**
 * Splits [shortcuts] (already in display order) into the screen's sections. While searching,
 * only matching shortcuts are shown, in a single list, so nothing appears twice.
 */
fun buildAppsSections(shortcuts: List<AppShortcut>, query: String): AppsSections {
    val q = query.trim()
    if (q.isNotEmpty()) {
        return AppsSections(emptyList(), emptyList(), shortcuts.filter { it.label.contains(q, ignoreCase = true) })
    }
    return AppsSections(
        favorites = shortcuts.filter { it.isFavorite },
        recent = shortcuts.filter { it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }.take(RECENT_LIMIT),
        all = shortcuts,
    )
}

/** True if [AppShortcutRepository.move] by [delta] would change anything (same favorite group). */
fun canMove(displayOrder: List<AppShortcut>, id: String, delta: Int): Boolean {
    val from = displayOrder.indexOfFirst { it.id == id }
    val to = from + delta
    return from >= 0 && to in displayOrder.indices && displayOrder[from].isFavorite == displayOrder[to].isFavorite
}

enum class ShortcutInputError { LABEL_BLANK, LABEL_TOO_LONG, LINK_BLANK, LINK_INVALID, LINK_SCHEME_NOT_ALLOWED }

const val MAX_LABEL_LENGTH = 40
const val MAX_LINK_LENGTH = 2048

/** Schemes that make no sense as a TV app link and could be abused. */
private val BLOCKED_SCHEMES = setOf("javascript", "file", "data", "content")

/** Validates the add/edit dialog. Returns the first problem, or null if the input can be saved. */
fun validateShortcut(label: String, link: String): ShortcutInputError? {
    val l = label.trim()
    if (l.isEmpty()) return ShortcutInputError.LABEL_BLANK
    if (l.length > MAX_LABEL_LENGTH) return ShortcutInputError.LABEL_TOO_LONG
    val url = link.trim()
    if (url.isEmpty()) return ShortcutInputError.LINK_BLANK
    return validateLink(url)
}

/**
 * The link must parse as an absolute URI with a scheme (e.g. `https://…`, `vnd.youtube:…`).
 * Web links need a host. Whitespace is rejected rather than silently encoded.
 */
private fun validateLink(link: String): ShortcutInputError? {
    if (link.length > MAX_LINK_LENGTH || link.any { it.isWhitespace() }) return ShortcutInputError.LINK_INVALID
    val uri = runCatching { URI(link) }.getOrNull() ?: return ShortcutInputError.LINK_INVALID
    val scheme = uri.scheme?.lowercase() ?: return ShortcutInputError.LINK_INVALID
    if (!uri.isAbsolute || uri.rawSchemeSpecificPart.isNullOrEmpty()) return ShortcutInputError.LINK_INVALID
    if (scheme in BLOCKED_SCHEMES) return ShortcutInputError.LINK_SCHEME_NOT_ALLOWED
    if ((scheme == "http" || scheme == "https") && uri.host.isNullOrEmpty()) return ShortcutInputError.LINK_INVALID
    return null
}

/** One or two letters for an app tile: initials of the first two words, e.g. "Prime Video" → "PV". */
fun monogram(label: String): String {
    val words = label.trim().split(' ', '\t').filter { it.isNotEmpty() }
    val initials = words.asSequence()
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
        .take(2)
        .joinToString("") { it.uppercase() }
    return initials.ifEmpty { label.trim().take(1).ifEmpty { "?" } }
}

/** Stable palette slot for a label, so a tile keeps its colour across launches. */
fun monogramColorIndex(label: String, paletteSize: Int): Int =
    if (paletteSize <= 0) 0 else Math.floorMod(label.trim().lowercase().hashCode(), paletteSize)
