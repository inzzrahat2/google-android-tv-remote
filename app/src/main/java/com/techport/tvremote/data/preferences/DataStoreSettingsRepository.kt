package com.techport.tvremote.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.techport.tvremote.domain.model.AppSettings
import com.techport.tvremote.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val settings: Flow<AppSettings> = store.data.map(::decode).distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs[KEY] = json.encodeToString(AppSettings.serializer(), transform(decode(prefs))) }
    }

    private fun decode(prefs: Preferences): AppSettings =
        prefs[KEY]?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()

    private companion object {
        val KEY = stringPreferencesKey("settings")
    }
}
