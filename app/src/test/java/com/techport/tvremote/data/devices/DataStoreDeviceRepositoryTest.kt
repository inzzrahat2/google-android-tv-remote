package com.techport.tvremote.data.devices

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.techport.tvremote.domain.model.TvDevice
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataStoreDeviceRepositoryTest {

    private fun TestScope.repository(dir: File = Files.createTempDirectory("ds").toFile()) =
        DataStoreDeviceRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, "test.preferences_pb") }),
        )

    private fun tv(id: String) = TvDevice(id, "TV $id", "10.0.0.$id", 6466, "androidtv")

    @Test
    fun upsertAppendsInOrderAndUpdatesInPlace() = runTest {
        val repo = repository()
        repo.upsert(tv("1"))
        repo.upsert(tv("2"))
        repo.upsert(tv("1").copy(name = "Renamed"))
        val list = repo.devices.first()
        assertEquals(listOf("1", "2"), list.map { it.id })
        assertEquals("Renamed", list.first().name)
    }

    @Test
    fun favoritesSortFirstAndReorderStaysWithinGroup() = runTest {
        val repo = repository()
        listOf("1", "2", "3").forEach { repo.upsert(tv(it)) }
        repo.setFavorite("3", true)
        assertEquals(listOf("3", "1", "2"), repo.devices.first().map { it.id })

        repo.move("1", -1) // would cross into favorites: ignored
        assertEquals(listOf("3", "1", "2"), repo.devices.first().map { it.id })

        repo.move("2", -1)
        assertEquals(listOf("3", "2", "1"), repo.devices.first().map { it.id })
    }

    @Test
    fun removeClearsLastSelected() = runTest {
        val repo = repository()
        repo.upsert(tv("1"))
        repo.setLastSelected("1")
        repo.remove("1")
        assertNull(repo.lastSelectedId.first())
        assertEquals(emptyList<TvDevice>(), repo.devices.first())
    }

    @Test
    fun renameIgnoresBlank() = runTest {
        val repo = repository()
        repo.upsert(tv("1"))
        repo.rename("1", "   ")
        assertEquals("TV 1", repo.get("1")!!.name)
        repo.rename("1", "  Den  ")
        assertEquals("Den", repo.get("1")!!.name)
    }

    @Test
    fun markConnectedUpdatesAddressAndTime() = runTest {
        val repo = repository()
        repo.upsert(tv("1"))
        repo.markConnected("1", "10.0.0.99", 6466, 1234L)
        val d = repo.get("1")!!
        assertEquals("10.0.0.99", d.host)
        assertEquals(1234L, d.lastConnectedAt)
    }
}
