package com.techport.tvremote.ui.quickactions

import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.model.QuickAction
import com.techport.tvremote.domain.model.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QuickActionsTest {
    private val yt = AppShortcut("yt", "YouTube", "https://www.youtube.com")
    private val home = QuickAction.Key(RemoteKey.HOME)
    private val mute = QuickAction.Key(RemoteKey.VOLUME_MUTE)

    @Test
    fun resolveDropsDeletedShortcutsAndDuplicates() {
        val resolved = resolveQuickActions(
            listOf(home, QuickAction.App("gone"), QuickAction.App("yt"), home),
            listOf(yt),
        )
        assertEquals(listOf(ResolvedQuickAction.Key(RemoteKey.HOME), ResolvedQuickAction.App(yt)), resolved)
    }

    @Test
    fun resolveAndSanitizeCapAtMax() {
        val many = QUICK_ACTION_KEYS.take(MAX_QUICK_ACTIONS + 3).map { QuickAction.Key(it) }
        assertEquals(MAX_QUICK_ACTIONS, resolveQuickActions(many, emptyList()).size)
        assertEquals(many.take(MAX_QUICK_ACTIONS), sanitizeQuickActions(many, emptyList()))
    }

    @Test
    fun sanitizeKeepsOrderAndDropsMissingApps() {
        assertEquals(
            listOf(mute, QuickAction.App("yt")),
            sanitizeQuickActions(listOf(mute, QuickAction.App("gone"), QuickAction.App("yt")), listOf(yt)),
        )
    }

    @Test
    fun toggleAddsRemovesAndRespectsLimit() {
        assertEquals(listOf(home), emptyList<QuickAction>().toggled(home))
        assertEquals(emptyList<QuickAction>(), listOf(home).toggled(home))
        val full = QUICK_ACTION_KEYS.take(MAX_QUICK_ACTIONS).map { QuickAction.Key(it) }
        val extra = QuickAction.Key(QUICK_ACTION_KEYS[MAX_QUICK_ACTIONS])
        assertEquals(full, full.toggled(extra))
    }

    @Test
    fun movedReordersWithinBounds() {
        val list = listOf(home, mute, QuickAction.App("yt"))
        assertEquals(listOf(mute, home, QuickAction.App("yt")), list.moved(1, -1))
        assertEquals(list, list.moved(0, -1))
        assertEquals(list, list.moved(2, 1))
    }

    @Test
    fun powerIsNeverOffered() {
        assertFalse(RemoteKey.POWER in QUICK_ACTION_KEYS)
    }
}
