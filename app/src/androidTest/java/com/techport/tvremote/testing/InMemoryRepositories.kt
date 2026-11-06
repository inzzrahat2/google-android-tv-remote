package com.techport.tvremote.testing

import com.techport.tvremote.domain.model.AppSettings
import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.repository.AppShortcutRepository
import com.techport.tvremote.domain.repository.DeviceRepository
import com.techport.tvremote.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [DeviceRepository] so UI tests never touch DataStore files. */
class InMemoryDeviceRepository(initial: List<TvDevice> = emptyList()) : DeviceRepository {
    private val stored = MutableStateFlow(initial)
    private val lastSelected = MutableStateFlow<String?>(null)

    val current: List<TvDevice> get() = stored.value

    override val devices: Flow<List<TvDevice>> =
        stored.map { list -> list.sortedWith(compareByDescending<TvDevice> { it.isFavorite }.thenBy { it.sortOrder }) }
    override val lastSelectedId: Flow<String?> = lastSelected

    override suspend fun get(id: String): TvDevice? = stored.value.firstOrNull { it.id == id }

    override suspend fun upsert(device: TvDevice) = stored.update { list ->
        if (list.any { it.id == device.id }) list.map { if (it.id == device.id) device else it }
        else list + device.copy(sortOrder = list.size)
    }

    override suspend fun remove(id: String) = stored.update { list -> list.filterNot { it.id == id } }

    override suspend fun rename(id: String, name: String) = edit(id) { it.copy(name = name) }

    override suspend fun setFavorite(id: String, favorite: Boolean) = edit(id) { it.copy(isFavorite = favorite) }

    override suspend fun move(id: String, delta: Int) = Unit

    override suspend fun markConnected(id: String, host: String, port: Int, at: Long) =
        edit(id) { it.copy(host = host, port = port, lastConnectedAt = at) }

    override suspend fun setLastSelected(id: String?) {
        lastSelected.value = id
    }

    private fun edit(id: String, transform: (TvDevice) -> TvDevice) =
        stored.update { list -> list.map { if (it.id == id) transform(it) else it } }
}

/** In-memory [SettingsRepository]; [state] doubles as the StateFlow the view models take. */
class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    override val settings: Flow<AppSettings> = _state

    override suspend fun update(transform: (AppSettings) -> AppSettings) = _state.update(transform)
}

/** In-memory [AppShortcutRepository]: favorites first, then user order. */
class InMemoryAppShortcutRepository(initial: List<AppShortcut> = emptyList()) : AppShortcutRepository {
    private val stored = MutableStateFlow(initial)

    val current: List<AppShortcut> get() = stored.value

    override val shortcuts: Flow<List<AppShortcut>> =
        stored.map { list -> list.sortedWith(compareByDescending<AppShortcut> { it.isFavorite }.thenBy { it.sortOrder }) }

    override suspend fun get(id: String): AppShortcut? = stored.value.firstOrNull { it.id == id }

    override suspend fun upsert(shortcut: AppShortcut) = stored.update { list ->
        if (list.any { it.id == shortcut.id }) list.map { if (it.id == shortcut.id) shortcut else it }
        else list + shortcut.copy(sortOrder = list.size)
    }

    override suspend fun remove(id: String) = stored.update { list -> list.filterNot { it.id == id } }

    override suspend fun setFavorite(id: String, favorite: Boolean) = edit(id) { it.copy(isFavorite = favorite) }

    override suspend fun move(id: String, delta: Int) = Unit

    override suspend fun markUsed(id: String, at: Long) = edit(id) { it.copy(lastUsedAt = at) }

    private fun edit(id: String, transform: (AppShortcut) -> AppShortcut) =
        stored.update { list -> list.map { if (it.id == id) transform(it) else it } }
}
