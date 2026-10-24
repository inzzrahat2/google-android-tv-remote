package com.techport.tvremote.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android 17 (API 37) blocks all LAN traffic, including NSD and plain sockets, for apps
 * targeting 37 until ACCESS_LOCAL_NETWORK is granted. Older releases grant it implicitly.
 * A blocked TCP connect surfaces only as a timeout, so check this before connecting.
 */
object LocalNetworkPermission {
    const val PERMISSION: String = Manifest.permission.ACCESS_LOCAL_NETWORK

    val isRuntimePermission: Boolean
        get() = Build.VERSION.SDK_INT >= 37

    fun isGranted(context: Context): Boolean =
        !isRuntimePermission || context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
}
