package com.techport.tvremote.domain.model

import kotlinx.serialization.Serializable

/**
 * An app the user can launch on the TV. The Android TV remote protocol cannot list installed
 * apps, so shortcuts are app links / deep links the user saved (optionally from presets).
 */
@Serializable
data class AppShortcut(
    val id: String,
    val label: String,
    /** URL or deep link sent with [RemoteCommand.LaunchApp]. */
    val link: String,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0,
    val lastUsedAt: Long? = null,
)
