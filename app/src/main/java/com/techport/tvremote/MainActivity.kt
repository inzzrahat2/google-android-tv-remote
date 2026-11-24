package com.techport.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techport.tvremote.ui.TvRemoteRoot
import com.techport.tvremote.ui.theme.TvRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = tvRemoteContainer
        setContent {
            val settings by container.settings.collectAsStateWithLifecycle()
            TvRemoteTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                TvRemoteRoot(container)
            }
        }
    }
}
