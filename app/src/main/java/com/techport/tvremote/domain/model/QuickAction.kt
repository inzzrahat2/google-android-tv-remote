package com.techport.tvremote.domain.model

import kotlinx.serialization.Serializable

/** A user-configured shortcut shown in the quick bar or a widget. */
@Serializable
sealed interface QuickAction {
    @Serializable
    data class Key(val key: RemoteKey) : QuickAction

    /** Launches a saved app shortcut by its [AppShortcut.id]. */
    @Serializable
    data class App(val shortcutId: String) : QuickAction
}
