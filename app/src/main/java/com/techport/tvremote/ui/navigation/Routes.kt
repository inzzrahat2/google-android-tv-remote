package com.techport.tvremote.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import com.techport.tvremote.R
import kotlinx.serialization.Serializable

@Serializable data object RemoteRoute
@Serializable data object AppsRoute
@Serializable data object DevicesRoute
@Serializable data object SettingsRoute
@Serializable data object DebugRoute

enum class TopLevelDestination(val route: Any, @StringRes val label: Int, val icon: ImageVector) {
    REMOTE(RemoteRoute, R.string.nav_remote, Icons.Rounded.SettingsRemote),
    APPS(AppsRoute, R.string.nav_apps, Icons.Rounded.Apps),
    DEVICES(DevicesRoute, R.string.nav_devices, Icons.Rounded.Tv),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Rounded.Settings),
}
