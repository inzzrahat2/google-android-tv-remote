package com.techport.tvremote.core.clipboard

/** Where clipboard text comes from. The Android implementation is [AndroidClipboardSource]. */
fun interface ClipboardSource {
    /** The current plain-text clip, or null if there is none. Called only on user action. */
    fun readText(): String?
}

sealed interface ClipboardRead {
    /** The user has not opted in (Settings › Keyboard › Paste to TV). Nothing was read. */
    data object Disabled : ClipboardRead

    /** The clipboard is empty or holds something that isn't text (an image, a file). */
    data object Empty : ClipboardRead

    /** Text ready to send. [truncated] is true if it was cut to [ClipboardReader.MAX_CHARS]. */
    class Text(val text: String, val truncated: Boolean) : ClipboardRead {
        override fun toString() = "Text(<${text.length} chars>, truncated=$truncated)"
    }
}

/**
 * Reads the phone clipboard for "Paste to TV".
 *
 * Privacy rules:
 * - It reads only when [read] is called, which must happen only on an explicit user action
 *   (a button tap), and only while the user has opted in ([isEnabled]). Nothing polls or
 *   observes the clipboard.
 * - The content is returned to the caller and never logged, stored or sent anywhere but the TV.
 * - There is deliberately no clipboard history: keeping past clips would retain passwords and
 *   one-time codes that password managers copy, long after the user meant to share them.
 */
class ClipboardReader(
    private val source: ClipboardSource,
    private val isEnabled: () -> Boolean,
) {
    val enabled: Boolean get() = isEnabled()

    fun read(): ClipboardRead {
        if (!isEnabled()) return ClipboardRead.Disabled
        val raw = runCatching { source.readText() }.getOrNull() ?: return ClipboardRead.Empty
        val text = normalize(raw)
        if (text.isEmpty()) return ClipboardRead.Empty
        if (text.length <= MAX_CHARS) return ClipboardRead.Text(text, truncated = false)
        // Don't cut a surrogate pair in half.
        val end = if (Character.isHighSurrogate(text[MAX_CHARS - 1])) MAX_CHARS - 1 else MAX_CHARS
        return ClipboardRead.Text(text.substring(0, end), truncated = true)
    }

    companion object {
        /** TV text fields are search boxes, URLs and logins; anything longer is almost always a mistake. */
        const val MAX_CHARS = 1_000

        /** TV fields are single-line: line breaks would submit early, so they become spaces. */
        internal fun normalize(raw: String): String =
            raw.trim().replace(LINE_BREAKS, " ")

        private val LINE_BREAKS = Regex("[\\r\\n\\u2028\\u2029]+")
    }
}
