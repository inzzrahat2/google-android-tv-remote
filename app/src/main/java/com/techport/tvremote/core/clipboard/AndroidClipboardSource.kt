package com.techport.tvremote.core.clipboard

import android.content.ClipboardManager
import android.content.Context

/**
 * Reads the primary clip's plain text. Android 10+ only lets the foreground app with focus
 * read the clipboard, which is exactly when the user taps "Paste to TV".
 *
 * Only `ClipData.Item.text` is used: URIs and intents are not coerced, because resolving a
 * content URI can do I/O on the caller's thread and its text is rarely what belongs on a TV.
 */
class AndroidClipboardSource(context: Context) : ClipboardSource {
    private val appContext = context.applicationContext

    override fun readText(): String? {
        val manager = appContext.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0)?.text?.toString()
    }
}
