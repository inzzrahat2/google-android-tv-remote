package com.techport.tvremote.data.apps

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.techport.tvremote.domain.model.AppShortcut
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreAppShortcutRepositoryTest {

    private fun TestScope.repository(dir: File = Files.createTempDirectory("apps").toFile()) =
        DataStoreAppShortcutRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, "test.preferences_pb") }),
        )

    private fun app(id: String) = AppShortcut(id, "App $id", "https://example.com/$id")

    @Test
    fun upsertAppendsTrimsAndUpdatesInPlace() = runTest {
        val repo = repository()
        repo.upsert(app("1"))
        repo.upsert(app("2").copy(label = "  Two  ", link = " https://two.example "))
        repo.upsert(app("1").copy(label = "Renamed"))
        val list = repo.shortcuts.first()
        assertEquals(listOf("1", "2"), list.map { it.id })
        assertEquals("Renamed", list[0].label)
        assertEquals("Two", list[1].label)
        assertEquals("https://two.example", list[1].link)
    }

    @Test
    fun favoritesSortFirstAndMoveStaysWithinGroup() = runTest {
        val repo = repository()
        listOf("1", "2", "3").forEach { repo.upsert(app(it)) }
        repo.setFavorite("3", true)
        assertEquals(listOf("3", "1", "2"), repo.shortcuts.first().map { it.id })

        repo.move("1", -1) // would cross into favorites: ignored
        assertEquals(listOf("3", "1", "2"), repo.shortcuts.first().map { it.id })

        repo.move("2", -1)
        assertEquals(listOf("3", "2", "1"), repo.shortcuts.first().map { it.id })
    }

    @Test
    fun editingKeepsPosition() = runTest {
        val repo = repository()
        listOf("1", "2", "3").forEach { repo.upsert(app(it)) }
        repo.move("3", -2)
        repo.upsert(app("3").copy(label = "Edited"))
        assertEquals(listOf("3", "1", "2"), repo.shortcuts.first().map { it.id })
    }

    @Test
    fun markUsedAndRemove() = runTest {
        val repo = repository()
        repo.upsert(app("1"))
        repo.markUsed("1", 42L)
        assertEquals(42L, repo.get("1")!!.lastUsedAt)
        repo.remove("1")
        assertNull(repo.get("1"))
        assertTrue(repo.shortcuts.first().isEmpty())
    }
}
