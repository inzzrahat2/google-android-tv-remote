package com.techport.tvremote

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.ProcessLifecycleOwner
import com.techport.tvremote.core.clipboard.AndroidClipboardSource
import com.techport.tvremote.core.clipboard.ClipboardReader
import com.techport.tvremote.core.connectivity.NetworkMonitor
import com.techport.tvremote.core.discovery.DiscoveryService
import com.techport.tvremote.core.haptics.Haptics
import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.core.voice.AudioRecordPcmSourceFactory
import com.techport.tvremote.core.voice.VoiceRecorder
import com.techport.tvremote.data.apps.DataStoreAppShortcutRepository
import com.techport.tvremote.data.devices.DataStoreDeviceRepository
import com.techport.tvremote.data.preferences.DataStoreSettingsRepository
import com.techport.tvremote.domain.model.AppSettings
import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.repository.AppShortcutRepository
import com.techport.tvremote.domain.repository.DeviceRepository
import com.techport.tvremote.domain.repository.SettingsRepository
import com.techport.tvremote.protocol.androidtv.AndroidTvProtocol
import com.techport.tvremote.protocol.core.ProtocolRegistry
import com.techport.tvremote.protocol.core.RemoteProtocol
import com.techport.tvremote.protocol.mock.MockTvProtocol
import com.techport.tvremote.service.connection.AutoConnector
import com.techport.tvremote.service.connection.ConnectionLifecycle
import com.techport.tvremote.service.connection.ConnectionManager
import com.techport.tvremote.widget.RemoteWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Hand-wired dependency graph. The graph is small enough that a DI framework would cost
 * more (build time, annotation processing) than it saves.
 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    val logger = AppLogger(echoToLogcat = BuildConfig.DEBUG)

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("tvremote") },
    )

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(dataStore)
    val deviceRepository: DeviceRepository = DataStoreDeviceRepository(dataStore)
    val appShortcutRepository: AppShortcutRepository = DataStoreAppShortcutRepository(dataStore)

    /** Null until settings were read from disk; one flow, so "loaded" and the values can't disagree. */
    val settingsOrNull: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<AppSettings> =
        settingsOrNull.map { it ?: AppSettings() }.stateIn(appScope, SharingStarted.Eagerly, AppSettings())

    val appShortcuts: StateFlow<List<AppShortcut>> =
        appShortcutRepository.shortcuts.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    val androidTv = AndroidTvProtocol(context, logger)

    val protocols = ProtocolRegistry(
        buildList<RemoteProtocol> {
            add(androidTv)
            if (BuildConfig.DEBUG) add(MockTvProtocol())
        },
    )

    val discovery = DiscoveryService(protocols)
    val haptics = Haptics(context)
    val networkMonitor = NetworkMonitor(context)
    val clipboardReader = ClipboardReader(AndroidClipboardSource(context)) { settings.value.clipboardEnabled }
    val voiceRecorder = VoiceRecorder(AudioRecordPcmSourceFactory(context))

    val connectionManager = ConnectionManager(
        scope = connectionScope,
        registry = protocols,
        devices = deviceRepository,
        logger = logger,
        autoReconnect = { settings.value.autoReconnect },
        // Older builds allowed 4 s, which cannot fit a re-resolve of a moved TV.
        connectTimeoutMs = { settings.value.connectionTimeoutSec.coerceAtLeast(10) * 1_000L },
    )

    private val autoConnector = AutoConnector(deviceRepository, connectionManager)

    val connectionLifecycle = ConnectionLifecycle(
        controller = connectionManager,
        scope = appScope,
        networkStatus = networkMonitor.status,
        onNetworkAvailable = connectionManager::onNetworkAvailable,
        connectPreferred = autoConnector::connectPreferred,
        logger = logger,
    )

    private val widgetUpdater = RemoteWidgetUpdater(context, connectionManager.state, deviceRepository, appScope)

    /**
     * Main thread (Application.onCreate). The lifecycle auto-connects on first foreground,
     * disconnects after a grace period in background, and retries on network changes.
     */
    fun start() {
        connectionLifecycle.start(ProcessLifecycleOwner.get().lifecycle)
        widgetUpdater.start()
    }

    /** Forgets a device everywhere: session, pinned certificate and saved record. */
    suspend fun forget(device: TvDevice) {
        connectionManager.release(device.id)
        protocols[device.protocolId]?.forget(device)
        deviceRepository.remove(device.id)
    }
}
