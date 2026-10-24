package com.techport.tvremote.domain.model

sealed interface RemoteCommand {
    data class Key(val key: RemoteKey, val action: KeyAction = KeyAction.CLICK) : RemoteCommand

    /** Replaces/insert text in the TV's focused text field. Never logged. */
    class Text(val text: String) : RemoteCommand {
        override fun toString() = "Text(<${text.length} chars>)"
    }

    /** Opens a deep link / app link on the TV. */
    data class LaunchApp(val link: String) : RemoteCommand
}
