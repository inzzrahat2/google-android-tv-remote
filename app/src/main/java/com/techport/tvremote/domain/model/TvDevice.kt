package com.techport.tvremote.domain.model

import kotlinx.serialization.Serializable

/**
 * A TV the user has paired with (or, for protocols without pairing, saved).
 *
 * Only what is needed to reconnect is stored. Secrets (client keys) never live here;
 * they belong to the protocol adapter's credential store.
 */
@Serializable
data class TvDevice(
    /** Stable identifier, unique per device across IP changes. Protocol-specific. */
    val id: String,
    val name: String,
    /** Last known address. Re-resolved through discovery when it stops answering. */
    val host: String,
    val port: Int,
    val protocolId: String,
    val model: String? = null,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0,
    val lastConnectedAt: Long? = null,
)

/** A TV seen on the network that may or may not be saved yet. */
data class DiscoveredDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocolId: String,
    val model: String? = null,
)
