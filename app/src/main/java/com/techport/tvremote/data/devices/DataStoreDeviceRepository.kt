package com.techport.tvremote.data.devices

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Saved devices as a small JSON list in DataStore. A household has a handful of TVs,
 * so a database would add weight without benefit.
 */
class DataStoreDeviceRepository(private val store: DataStore<Preferences>) : DeviceRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(TvDevice.serializer())

    override val devices: Flow<List<TvDevice>> =
        store.data.map { displayOrder(decode(it)) }.distinctUntilChanged()

    override val lastSelectedId: Flow<String?> =
        store.data.map { it[KEY_LAST_SELECTED] }.distinctUntilChanged()

    override suspend fun get(id: String): TvDevice? = decode(store.data.first()).find { it.id == id }

    override suspend fun upsert(device: TvDevice) = mutate { list ->
        val existing = list.indexOfFirst { it.id == device.id }
        if (existing >= 0) {
            list.toMutableList().also { it[existing] = device }
        } else {
            list + device.copy(sortOrder = (list.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        }
    }

    override suspend fun remove(id: String) {
        store.edit { prefs ->
            prefs[KEY_DEVICES] = encode(decode(prefs).filterNot { it.id == id })
            if (prefs[KEY_LAST_SELECTED] == id) prefs.remove(KEY_LAST_SELECTED)
        }
    }

    override suspend fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate { list -> list.map { if (it.id == id) it.copy(name = trimmed) else it } }
    }

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
        ordered.mapIndexed { index, device -> device.copy(sortOrder = index) }
    }

    override suspend fun markConnected(id: String, host: String, port: Int, at: Long) = mutate { list ->
        list.map { if (it.id == id) it.copy(host = host, port = port, lastConnectedAt = at) else it }
    }

    override suspend fun setLastSelected(id: String?) {
        store.edit { prefs -> if (id == null) prefs.remove(KEY_LAST_SELECTED) else prefs[KEY_LAST_SELECTED] = id }
    }

    private suspend fun mutate(transform: (List<TvDevice>) -> List<TvDevice>) {
        store.edit { prefs -> prefs[KEY_DEVICES] = encode(transform(decode(prefs))) }
    }

    private fun decode(prefs: Preferences): List<TvDevice> =
        prefs[KEY_DEVICES]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun encode(list: List<TvDevice>): String = json.encodeToString(serializer, list)

    private fun displayOrder(list: List<TvDevice>) =
        list.sortedWith(compareByDescending<TvDevice> { it.isFavorite }.thenBy { it.sortOrder })

    private companion object {
        val KEY_DEVICES = stringPreferencesKey("devices")
        val KEY_LAST_SELECTED = stringPreferencesKey("last_selected_device")
    }
}
