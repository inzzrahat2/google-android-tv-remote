package com.techport.tvremote.ui.apps

import com.techport.tvremote.data.apps.AppLinkSuggestions
import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.TvDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsLogicTest {

    private fun app(id: String, label: String = "App $id", fav: Boolean = false, used: Long? = null) =
        AppShortcut(id, label, "https://example.com/$id", isFavorite = fav, lastUsedAt = used)

    @Test
    fun sectionsSplitFavoritesRecentAndAll() {
        val list = listOf(
            app("a", fav = true, used = 5),
            app("b", used = 50),
            app("c", used = 10),
            app("d", used = 40),
            app("e", used = 30),
            app("f"),
        )
        val s = buildAppsSections(list, "")
        assertEquals(listOf("a"), s.favorites.map { it.id })
        assertEquals(listOf("b", "d", "e", "c"), s.recent.map { it.id }) // newest first, top 4
        assertEquals(list, s.all)
    }

    @Test
    fun searchFiltersByLabelIgnoringCaseIntoOneList() {
        val list = listOf(app("1", "YouTube", fav = true, used = 1), app("2", "YouTube Music"), app("3", "Netflix"))
        val s = buildAppsSections(list, "  youtube ")
        assertTrue(s.favorites.isEmpty())
        assertTrue(s.recent.isEmpty())
        assertEquals(listOf("1", "2"), s.all.map { it.id })
    }

    @Test
    fun validationRequiresLabelAndAbsoluteLink() {
        assertEquals(ShortcutInputError.LABEL_BLANK, validateShortcut("  ", "https://a.com"))
        assertEquals(ShortcutInputError.LABEL_TOO_LONG, validateShortcut("x".repeat(MAX_LABEL_LENGTH + 1), "https://a.com"))
        assertEquals(ShortcutInputError.LINK_BLANK, validateShortcut("A", " "))
        assertEquals(ShortcutInputError.LINK_INVALID, validateShortcut("A", "youtube.com"))
        assertEquals(ShortcutInputError.LINK_INVALID, validateShortcut("A", "https://"))
        assertEquals(ShortcutInputError.LINK_INVALID, validateShortcut("A", "https://exa mple.com"))
        assertEquals(ShortcutInputError.LINK_INVALID, validateShortcut("A", "http:/path-only"))
        assertEquals(ShortcutInputError.LINK_SCHEME_NOT_ALLOWED, validateShortcut("A", "javascript:alert(1)"))
        assertEquals(ShortcutInputError.LINK_SCHEME_NOT_ALLOWED, validateShortcut("A", "file:///sdcard/x"))
    }

    @Test
    fun validationAcceptsWebAndDeepLinks() {
        assertNull(validateShortcut("YouTube", " https://www.youtube.com "))
        assertNull(validateShortcut("Spotify", "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"))
        assertNull(validateShortcut("Market", "market://details?id=com.example"))
        assertNull(validateShortcut("Video", "vnd.youtube:dQw4w9WgXcQ"))
    }

    @Test
    fun monogramUsesInitials() {
        assertEquals("PV", monogram("Prime Video"))
        assertEquals("Y", monogram("YouTube"))
        assertEquals("D", monogram("Disney+"))
        assertEquals("YM", monogram("  youtube   music tv "))
        assertEquals("?", monogram("   "))
        assertEquals("+", monogram("+"))
    }

    @Test
    fun monogramColorIsStableAndInRange() {
        val i = monogramColorIndex("Netflix", 4)
        assertTrue(i in 0..3)
        assertEquals(i, monogramColorIndex(" netflix ", 4))
        assertEquals(0, monogramColorIndex("x", 0))
    }

    @Test
    fun canMoveOnlyWithinGroup() {
        val list = listOf(app("a", fav = true), app("b"), app("c"))
        assertFalse(canMove(list, "a", -1))
        assertFalse(canMove(list, "a", 1)) // next is not a favorite
        assertFalse(canMove(list, "b", -1))
        assertTrue(canMove(list, "b", 1))
        assertTrue(canMove(list, "c", -1))
        assertFalse(canMove(list, "missing", 1))
    }

    @Test
    fun availabilityFollowsConnectionAndCapability() {
        val tv = TvDevice("1", "TV", "10.0.0.1", 6466, "mock")
        assertEquals(LaunchAvailability.NOT_CONNECTED, launchAvailability(ConnectionState.Idle))
        assertEquals(LaunchAvailability.NOT_CONNECTED, launchAvailability(ConnectionState.Connecting(tv)))
        assertEquals(
            LaunchAvailability.UNSUPPORTED,
            launchAvailability(ConnectionState.Connected(tv, RemoteCapabilities(dpad = true))),
        )
        assertEquals(
            LaunchAvailability.READY,
            launchAvailability(ConnectionState.Connected(tv, RemoteCapabilities(appLaunch = true))),
        )
    }

    @Test
    fun suggestionsHideAlreadySavedLinks() {
        val saved = listOf(AppShortcut("1", "My YT", "https://WWW.youtube.com/"))
        val remaining = AppLinkSuggestions.notSaved(saved)
        assertFalse(remaining.any { it.label == "YouTube" })
        assertEquals(AppLinkSuggestions.ALL.size - 1, remaining.size)
    }
}
