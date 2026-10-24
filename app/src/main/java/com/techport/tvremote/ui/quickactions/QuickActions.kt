package com.techport.tvremote.ui.quickactions

import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.model.QuickAction
import com.techport.tvremote.domain.model.RemoteKey

/** The quick bar holds at most this many actions. */
const val MAX_QUICK_ACTIONS = 6

/**
 * Keys offered in the editor. POWER is left out on purpose: the bar would bypass the
 * power-confirmation setting. D-pad and volume already have permanent controls.
 */
val QUICK_ACTION_KEYS: List<RemoteKey> = listOf(
    RemoteKey.HOME,
    RemoteKey.BACK,
    RemoteKey.SETTINGS,
    RemoteKey.APP_SWITCH,
    RemoteKey.MENU,
    RemoteKey.GUIDE,
    RemoteKey.ASSIST,
    RemoteKey.SEARCH,
    RemoteKey.INFO,
    RemoteKey.CAPTIONS,
    RemoteKey.VOLUME_MUTE,
    RemoteKey.MEDIA_PLAY_PAUSE,
    RemoteKey.MEDIA_STOP,
    RemoteKey.CHANNEL_UP,
    RemoteKey.CHANNEL_DOWN,
    RemoteKey.TV_INPUT,
    RemoteKey.TV_INPUT_HDMI_1,
    RemoteKey.TV_INPUT_HDMI_2,
    RemoteKey.TV_INPUT_HDMI_3,
    RemoteKey.TV_INPUT_HDMI_4,
)

/** A quick action ready to render: app actions carry their (still existing) shortcut. */
sealed interface ResolvedQuickAction {
    data class Key(val key: RemoteKey) : ResolvedQuickAction
    data class App(val shortcut: AppShortcut) : ResolvedQuickAction
}

/**
 * Resolves saved actions for display. App actions whose shortcut was deleted are dropped,
 * duplicates keep their first position, and the result is capped at [MAX_QUICK_ACTIONS].
 */
fun resolveQuickActions(actions: List<QuickAction>, shortcuts: List<AppShortcut>): List<ResolvedQuickAction> {
    val byId = shortcuts.associateBy { it.id }
    return actions.distinct().mapNotNull { action ->
        when (action) {
            is QuickAction.Key -> ResolvedQuickAction.Key(action.key)
            is QuickAction.App -> byId[action.shortcutId]?.let { ResolvedQuickAction.App(it) }
        }
    }.take(MAX_QUICK_ACTIONS)
}

/** The list to persist: same rules as [resolveQuickActions], kept as [QuickAction]s. */
fun sanitizeQuickActions(actions: List<QuickAction>, shortcuts: List<AppShortcut>): List<QuickAction> {
    val ids = shortcuts.mapTo(HashSet()) { it.id }
    return actions.distinct()
        .filter { it !is QuickAction.App || it.shortcutId in ids }
        .take(MAX_QUICK_ACTIONS)
}

/** Adds [action] at the end (if there is room) or removes it if already selected. */
fun List<QuickAction>.toggled(action: QuickAction): List<QuickAction> = when {
    action in this -> this - action
    size >= MAX_QUICK_ACTIONS -> this
    else -> this + action
}

/** Moves the item at [index] by [delta]; out-of-range moves leave the list unchanged. */
fun List<QuickAction>.moved(index: Int, delta: Int): List<QuickAction> {
    val to = index + delta
    if (index !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(index)) }
}
