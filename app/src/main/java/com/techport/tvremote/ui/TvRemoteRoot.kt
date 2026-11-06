package com.techport.tvremote.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.techport.tvremote.ui.apps.AppsViewModel
import com.techport.tvremote.ui.quickactions.QuickActionsEditor
import com.techport.tvremote.ui.components.LocalPressFeedback
import com.techport.tvremote.ui.debug.DebugScreen
import com.techport.tvremote.ui.debug.DebugViewModel
import com.techport.tvremote.ui.devices.DevicesScreen
import com.techport.tvremote.ui.devices.DevicesViewModel
import com.techport.tvremote.ui.navigation.AppsRoute
import com.techport.tvremote.ui.navigation.DebugRoute
import com.techport.tvremote.ui.navigation.DevicesRoute
import com.techport.tvremote.ui.navigation.KeyboardRoute
import com.techport.tvremote.ui.keyboard.KeyboardScreen
import com.techport.tvremote.ui.keyboard.KeyboardViewModel
import com.techport.tvremote.ui.keyboard.VoiceButton
import com.techport.tvremote.ui.keyboard.VoiceViewModel
import com.techport.tvremote.ui.onboarding.OnboardingScreen
import com.techport.tvremote.domain.model.ConnectionState
import kotlinx.coroutines.launch
import com.techport.tvremote.ui.navigation.RemoteRoute
import com.techport.tvremote.ui.navigation.SettingsRoute
import com.techport.tvremote.ui.navigation.TopLevelDestination
import com.techport.tvremote.ui.remote.RemoteScreen
import com.techport.tvremote.ui.remote.RemoteViewModel
import com.techport.tvremote.ui.settings.SettingsScreen
import com.techport.tvremote.ui.settings.SettingsViewModel

@Composable
fun TvRemoteRoot(container: AppContainer) {
    val loadedSettings by container.settingsOrNull.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var startOnDevices by rememberSaveable { mutableStateOf(false) }

    // Blank until settings are read, so returning users never see onboarding flash.
    val settings = loadedSettings ?: run {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    if (!settings.onboardingDone) {
        fun finish(findTv: Boolean) {
            startOnDevices = findTv
            scope.launch { container.settingsRepository.update { it.copy(onboardingDone = true) } }
        }
        OnboardingScreen(onFindTv = { finish(true) }, onSkip = { finish(false) })
        return
    }

    val nav = rememberNavController()
    val haptics = container.haptics
    val feedback = remember(settings.haptics) { { haptics.click(settings.haptics) } }
    val entry by nav.currentBackStackEntryAsState()
    // Full-screen routes manage their own insets (the keyboard screen sits on the IME).
    val fullScreen = entry?.destination?.hasRoute(KeyboardRoute::class) == true
    val start: Any = if (startOnDevices) DevicesRoute else RemoteRoute

    // One NavHost moved between layouts (rotation, full-screen keyboard) instead of recreated, so
    // screens keep their rememberSaveable state (touchpad mode, open sheets, search text).
    val host = remember(nav, start) { movableContentOf { Destinations(nav, container, start) } }

    CompositionLocalProvider(LocalPressFeedback provides feedback) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            when {
                fullScreen -> Box(Modifier.fillMaxSize()) { host() }
                wide -> Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Rail(nav)
                    Box(Modifier.weight(1f)) { host() }
                }
                else -> Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { BottomBar(nav) },
                ) { padding ->
                    Box(Modifier.padding(padding)) { host() }
                }
            }
        }
    }
}

@Composable
private fun Destinations(nav: NavHostController, container: AppContainer, start: Any) {
    val openDevices = { nav.navigateTopLevel(TopLevelDestination.DEVICES) }
    NavHost(nav, startDestination = start) {
        composable<RemoteRoute> {
            val vm = viewModel {
                RemoteViewModel(
                    controller = container.connectionManager,
                    devices = container.deviceRepository,
                    networkStatus = container.networkMonitor.status,
                    settings = container.settings,
                    shortcuts = container.appShortcuts,
                    shortcutRepository = container.appShortcutRepository,
                )
            }
            val voiceVm = viewModel { VoiceViewModel(container.connectionManager, container.voiceRecorder) }
            RemoteScreen(
                vm,
                onOpenDevices = openDevices,
                onOpenKeyboard = { nav.navigate(KeyboardRoute) { launchSingleTop = true } },
                onOpenApps = { nav.navigateTopLevel(TopLevelDestination.APPS) },
                voiceButton = { VoiceButton(voiceVm, size = 56.dp) },
            )
        }
        composable<KeyboardRoute> {
            val vm = viewModel { KeyboardViewModel(container.connectionManager, container.settings, container.clipboardReader) }
            val voiceVm = viewModel { VoiceViewModel(container.connectionManager, container.voiceRecorder) }
            val state by container.connectionManager.state.collectAsStateWithLifecycle()
            val voice = (state as? ConnectionState.Connected)?.capabilities?.voice == true
            KeyboardScreen(
                vm,
                onClose = { nav.popBackStack() },
                // Pop the keyboard first, or it is saved as the Remote tab's state and reappears there.
                onOpenSettings = {
                    nav.popBackStack()
                    nav.navigateTopLevel(TopLevelDestination.SETTINGS)
                },
                voiceButton = if (voice) {
                    { VoiceButton(voiceVm) }
                } else {
                    null
                },
            )
        }
        composable<AppsRoute> {
            val vm = viewModel { AppsViewModel(container.appShortcutRepository, container.connectionManager) }
            AppsScreen(vm)
        }
        composable<DevicesRoute> {
            val vm = viewModel {
                DevicesViewModel(
                    devices = container.deviceRepository,
                    controller = container.connectionManager,
                    discovery = container.discovery,
                    protocols = container.protocols,
                    forgetDevice = container::forget,
                    manualTarget = { host -> container.androidTv.manualTarget(host) },
                    logger = container.logger,
                )
            }
            DevicesScreen(vm, onOpenRemote = { nav.navigateTopLevel(TopLevelDestination.REMOTE) })
        }
        composable<SettingsRoute> {
            val vm = viewModel { SettingsViewModel(container.settingsRepository, container.settings) }
            val shortcuts by container.appShortcuts.collectAsStateWithLifecycle()
            val settings by container.settings.collectAsStateWithLifecycle()
            SettingsScreen(
                vm,
                showDebug = BuildConfig.DEBUG,
                onOpenDebug = { nav.navigate(DebugRoute) },
                quickActionsEditor = { onDismiss ->
                    QuickActionsEditor(
                        current = settings.quickActions,
                        shortcuts = shortcuts,
                        onSave = { list ->
                            vm.update { it.copy(quickActions = list) }
                            onDismiss()
                        },
                        onDismiss = onDismiss,
                    )
                },
            )
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
