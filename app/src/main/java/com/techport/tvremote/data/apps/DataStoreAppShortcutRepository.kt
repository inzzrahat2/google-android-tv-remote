package com.techport.tvremote.data.apps

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.repository.AppShortcutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Saved app shortcuts as a JSON list under their own key in the shared preferences DataStore,
 * the same approach as [com.techport.tvremote.data.devices.DataStoreDeviceRepository].
 */
class DataStoreAppShortcutRepository(private val store: DataStore<Preferences>) : AppShortcutRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(AppShortcut.serializer())

    override val shortcuts: Flow<List<AppShortcut>> =
        store.data.map { displayOrder(decode(it)) }.distinctUntilChanged()

    override suspend fun get(id: String): AppShortcut? = decode(store.data.first()).find { it.id == id }

    /** Updates in place (keeping its position) or appends a new shortcut at the end. */
    override suspend fun upsert(shortcut: AppShortcut) = mutate { list ->
        val cleaned = shortcut.copy(label = shortcut.label.trim(), link = shortcut.link.trim())
        val existing = list.indexOfFirst { it.id == cleaned.id }
        if (existing >= 0) {
            list.toMutableList().also { it[existing] = cleaned.copy(sortOrder = list[existing].sortOrder) }
        } else {
            list + cleaned.copy(sortOrder = (list.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        }
    }

    override suspend fun remove(id: String) = mutate { list -> list.filterNot { it.id == id } }

    override suspend fun setFavorite(id: String, favorite: Boolean) = mutate { list ->
        list.map { if (it.id == id) it.copy(isFavorite = favorite) else it }
    }

    override suspend fun move(id: String, delta: Int) = mutate { list ->
        val ordered = displayOrder(list).toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        val to = from + delta
        // Favorites always sort first, so only swap within the same group.
        if (from < 0 || to !in ordered.indices || ordered[from].isFavorite != ordered[to].isFavorite) {
            return@mutate list
        }
        ordered.add(to, ordered.removeAt(from))
        ordered.mapIndexed { index, shortcut -> shortcut.copy(sortOrder = index) }
    }

    override suspend fun markUsed(id: String, at: Long) = mutate { list ->
        list.map { if (it.id == id) it.copy(lastUsedAt = at) else it }
    }

    private suspend fun mutate(transform: (List<AppShortcut>) -> List<AppShortcut>) {
        store.edit { prefs -> prefs[KEY_SHORTCUTS] = encode(transform(decode(prefs))) }
    }

    private fun decode(prefs: Preferences): List<AppShortcut> =
        prefs[KEY_SHORTCUTS]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun encode(list: List<AppShortcut>): String = json.encodeToString(serializer, list)

    private fun displayOrder(list: List<AppShortcut>) =
        list.sortedWith(compareByDescending<AppShortcut> { it.isFavorite }.thenBy { it.sortOrder })

    private companion object {
        val KEY_SHORTCUTS = stringPreferencesKey("app_shortcuts")
    }
}
