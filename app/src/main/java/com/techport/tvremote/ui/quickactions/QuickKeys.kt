package com.techport.tvremote.ui.quickactions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.RemoteKey

/** User-facing name for a key offered in quick actions or the inputs sheet. */
@Composable
fun quickKeyLabel(key: RemoteKey): String = when (key) {
    RemoteKey.HOME -> stringResource(R.string.key_home)
    RemoteKey.BACK -> stringResource(R.string.key_back)
    RemoteKey.SETTINGS -> stringResource(R.string.key_settings)
    RemoteKey.APP_SWITCH -> stringResource(R.string.key_recents)
    RemoteKey.MENU -> stringResource(R.string.key_menu)
    RemoteKey.GUIDE -> stringResource(R.string.key_guide)
    RemoteKey.ASSIST -> stringResource(R.string.key_assistant)
    RemoteKey.SEARCH -> stringResource(R.string.quickactions_key_search)
    RemoteKey.INFO -> stringResource(R.string.quickactions_key_info)
    RemoteKey.CAPTIONS -> stringResource(R.string.quickactions_key_captions)
    RemoteKey.VOLUME_MUTE -> stringResource(R.string.key_mute)
    RemoteKey.MEDIA_PLAY_PAUSE -> stringResource(R.string.key_play_pause)
    RemoteKey.MEDIA_STOP -> stringResource(R.string.key_stop)
    RemoteKey.CHANNEL_UP -> stringResource(R.string.key_channel_up)
    RemoteKey.CHANNEL_DOWN -> stringResource(R.string.key_channel_down)
    RemoteKey.TV_INPUT -> stringResource(R.string.key_input)
    RemoteKey.TV_INPUT_HDMI_1 -> stringResource(R.string.inputs_hdmi, 1)
    RemoteKey.TV_INPUT_HDMI_2 -> stringResource(R.string.inputs_hdmi, 2)
    RemoteKey.TV_INPUT_HDMI_3 -> stringResource(R.string.inputs_hdmi, 3)
    RemoteKey.TV_INPUT_HDMI_4 -> stringResource(R.string.inputs_hdmi, 4)
    else -> key.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun quickKeyIcon(key: RemoteKey): ImageVector? = when (key) {
    RemoteKey.HOME -> Icons.Rounded.Home
    RemoteKey.BACK -> Icons.AutoMirrored.Rounded.ArrowBack
    RemoteKey.SETTINGS -> Icons.Rounded.Settings
    RemoteKey.APP_SWITCH -> Icons.Rounded.ViewCarousel
    RemoteKey.MENU -> Icons.Rounded.Menu
    RemoteKey.GUIDE -> Icons.Rounded.LiveTv
    RemoteKey.ASSIST -> Icons.Rounded.Assistant
    RemoteKey.SEARCH -> Icons.Rounded.Search
    RemoteKey.INFO -> Icons.Rounded.Info
    RemoteKey.CAPTIONS -> Icons.Rounded.ClosedCaption
    RemoteKey.VOLUME_MUTE -> Icons.AutoMirrored.Rounded.VolumeOff
    RemoteKey.MEDIA_PLAY_PAUSE -> Icons.Rounded.PlayArrow
    RemoteKey.MEDIA_STOP -> Icons.Rounded.Stop
    RemoteKey.CHANNEL_UP -> Icons.Rounded.KeyboardArrowUp
    RemoteKey.CHANNEL_DOWN -> Icons.Rounded.KeyboardArrowDown
    RemoteKey.TV_INPUT -> Icons.Rounded.Input
    RemoteKey.TV_INPUT_HDMI_1, RemoteKey.TV_INPUT_HDMI_2,
    RemoteKey.TV_INPUT_HDMI_3, RemoteKey.TV_INPUT_HDMI_4,
    -> Icons.Rounded.SettingsInputHdmi
    else -> null
}

/** Icon for app shortcuts in the quick bar and editor. */
val QuickAppIcon: ImageVector get() = Icons.Rounded.Apps
