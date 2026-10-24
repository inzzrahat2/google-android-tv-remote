package com.techport.tvremote.widget

import android.content.Context
import com.techport.tvremote.TvRemoteApp
import com.techport.tvremote.service.connection.ConnectionLifecycle

/**
 * Implemented by AppContainer so widgets can reach the [ConnectionLifecycle] without a hard
 * dependency on how the container is wired. When it is not implemented, widget presses still
 * work but do not re-arm the background disconnect timer.
 */
interface PlatformHost {
    val connectionLifecycle: ConnectionLifecycle
}

internal val Context.tvRemoteContainer
    get() = (applicationContext as TvRemoteApp).container

internal val Context.connectionLifecycleOrNull: ConnectionLifecycle?
    get() = (tvRemoteContainer as? PlatformHost)?.connectionLifecycle
