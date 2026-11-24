package com.techport.tvremote

import android.app.Application
import android.content.Context

class TvRemoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

/** The app's dependency graph, from any context (activity, service, receiver, widget). */
val Context.tvRemoteContainer: AppContainer
    get() = (applicationContext as TvRemoteApp).container
