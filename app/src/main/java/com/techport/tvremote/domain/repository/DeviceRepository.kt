package com.techport.tvremote.domain.repository

import com.techport.tvremote.domain.model.TvDevice
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    /** Saved devices: favorites first, then by user order. */
    val devices: Flow<List<TvDevice>>
    val lastSelectedId: Flow<String?>

    suspend fun get(id: String): TvDevice?
    suspend fun upsert(device: TvDevice)
    suspend fun remove(id: String)
    suspend fun rename(id: String, name: String)
    suspend fun setFavorite(id: String, favorite: Boolean)

    /** Moves the device one slot up (-1) or down (+1) in the list. */
    suspend fun move(id: String, delta: Int)
    suspend fun markConnected(id: String, host: String, port: Int, at: Long)
    suspend fun setLastSelected(id: String?)
}
