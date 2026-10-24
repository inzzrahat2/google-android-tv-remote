package com.techport.tvremote.domain.repository

import com.techport.tvremote.domain.model.AppShortcut
import kotlinx.coroutines.flow.Flow

interface AppShortcutRepository {
    /** Favorites first, then user order. */
    val shortcuts: Flow<List<AppShortcut>>

    suspend fun get(id: String): AppShortcut?
    suspend fun upsert(shortcut: AppShortcut)
    suspend fun remove(id: String)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun move(id: String, delta: Int)
    suspend fun markUsed(id: String, at: Long)
}
