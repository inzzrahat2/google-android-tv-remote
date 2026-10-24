package com.techport.tvremote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.techport.tvremote.AppContainer
import com.techport.tvremote.BuildConfig
import com.techport.tvremote.ui.apps.AppsScreen
import com.techport.tvremote.ui.components.LocalPressFeedback
import com.techport.tvremote.ui.debug.DebugScreen
import com.techport.tvremote.ui.debug.DebugViewModel
import com.techport.tvremote.ui.devices.DevicesScreen
import com.techport.tvremote.ui.devices.DevicesViewModel
import com.techport.tvremote.ui.navigation.AppsRoute
import com.techport.tvremote.ui.navigation.DebugRoute
import com.techport.tvremote.ui.navigation.DevicesRoute
import com.techport.tvremote.ui.navigation.RemoteRoute
import com.techport.tvremote.ui.navigation.SettingsRoute
import com.techport.tvremote.ui.navigation.TopLevelDestination
import com.techport.tvremote.ui.remote.RemoteScreen
import com.techport.tvremote.ui.remote.RemoteViewModel
import com.techport.tvremote.ui.settings.SettingsScreen
import com.techport.tvremote.ui.settings.SettingsViewModel

@Composable
fun TvRemoteRoot(container: AppContainer) {
    val nav = rememberNavController()
    val settings by container.settings.collectAsStateWithLifecycle()
    val haptics = container.haptics
    val feedback = remember(settings.haptics) { { haptics.click(settings.haptics) } }

    CompositionLocalProvider(LocalPressFeedback provides feedback) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            if (wide) {
                Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Rail(nav)
                    Box(Modifier.weight(1f)) { Destinations(nav, container) }
                }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { BottomBar(nav) },
                ) { padding ->
                    Box(Modifier.padding(padding)) { Destinations(nav, container) }
                }
            }
        }
    }
}

@Composable
private fun Destinations(nav: NavHostController, container: AppContainer) {
    val openDevices = { nav.navigateTopLevel(TopLevelDestination.DEVICES) }
    NavHost(nav, startDestination = RemoteRoute) {
        composable<RemoteRoute> {
            val vm = viewModel { RemoteViewModel(container.connectionManager, container.deviceRepository, container.settings) }
            RemoteScreen(vm, onOpenDevices = openDevices)
        }
        composable<AppsRoute> { AppsScreen() }
        composable<DevicesRoute> {
            val vm = viewModel {
                DevicesViewModel(
                    devices = container.deviceRepository,
                    controller = container.connectionManager,
                    discovery = container.discovery,
                    protocols = container.protocols,
                    forgetDevice = container::forget,
                    logger = container.logger,
                )
            }
            DevicesScreen(vm, onOpenRemote = { nav.navigateTopLevel(TopLevelDestination.REMOTE) })
        }
        composable<SettingsRoute> {
            val vm = viewModel { SettingsViewModel(container.settingsRepository, container.settings) }
            SettingsScreen(vm, showDebug = BuildConfig.DEBUG, onOpenDebug = { nav.navigate(DebugRoute) })
        }
        if (BuildConfig.DEBUG) {
            composable<DebugRoute> {
                val vm = viewModel { DebugViewModel(container.connectionManager, container.logger) }
                DebugScreen(vm)
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    NavigationBar {
        TopLevelDestination.entries.forEach { dest ->
            val selected = entry?.destination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { nav.navigateTopLevel(dest) },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(stringResource(dest.label)) },
            )
        }
    }
}

@Composable
private fun Rail(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    NavigationRail {
        TopLevelDestination.entries.forEach { dest ->
            val selected = entry?.destination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
            NavigationRailItem(
                selected = selected,
                onClick = { nav.navigateTopLevel(dest) },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(stringResource(dest.label)) },
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(dest: TopLevelDestination) {
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
