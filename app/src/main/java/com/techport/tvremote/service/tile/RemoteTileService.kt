package com.techport.tvremote.service.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.techport.tvremote.MainActivity
import com.techport.tvremote.R
import com.techport.tvremote.TvRemoteApp
import com.techport.tvremote.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** What the tile shows for a connection state. Pure so it can be tested. */
data class TileModel(val active: Boolean, val subtitle: TileSubtitle, val deviceName: String?)

enum class TileSubtitle { CONNECTED, CONNECTING, NOT_CONNECTED, NO_TV }

fun tileModel(state: ConnectionState): TileModel = when (state) {
    ConnectionState.Idle -> TileModel(false, TileSubtitle.NO_TV, null)
    is ConnectionState.Connected -> TileModel(true, TileSubtitle.CONNECTED, state.device.name)
    is ConnectionState.Connecting -> TileModel(false, TileSubtitle.CONNECTING, state.device.name)
    is ConnectionState.Reconnecting -> TileModel(false, TileSubtitle.CONNECTING, state.device.name)
    is ConnectionState.PairingRequired -> TileModel(false, TileSubtitle.NOT_CONNECTED, state.device.name)
    is ConnectionState.Disconnected -> TileModel(false, TileSubtitle.NOT_CONNECTED, state.device.name)
}

/**
 * Quick Settings tile "TV Remote": opens the remote. While the shade is open (and only then)
 * it mirrors the connection state the app already holds; it never connects by itself.
 */
class RemoteTileService : TileService() {
    private var listening: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val state = (applicationContext as TvRemoteApp).container.connectionManager.state
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        listening?.cancel()
        listening = scope
        state.map(::tileModel).distinctUntilChanged().onEach(::render).launchIn(scope)
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listening?.cancel()
        listening = null
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::openApp) else openApp()
    }

    private fun render(model: TileModel) {
        val tile = qsTile ?: return
        tile.state = if (model.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.platform_tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (model.subtitle) {
                TileSubtitle.CONNECTED -> model.deviceName
                TileSubtitle.CONNECTING -> getString(R.string.state_connecting)
                TileSubtitle.NOT_CONNECTED -> getString(R.string.platform_tile_not_connected)
                TileSubtitle.NO_TV -> null
            }
        }
        tile.contentDescription = getString(R.string.platform_tile_label)
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
