package com.techport.tvremote

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.techport.tvremote.core.connectivity.NetworkMonitor
import com.techport.tvremote.core.discovery.DiscoveryService
import com.techport.tvremote.core.haptics.Haptics
import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.data.devices.DataStoreDeviceRepository
import com.techport.tvremote.data.preferences.DataStoreSettingsRepository
import com.techport.tvremote.domain.model.AppSettings
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.repository.DeviceRepository
import com.techport.tvremote.domain.repository.SettingsRepository
import com.techport.tvremote.protocol.core.ProtocolRegistry
import com.techport.tvremote.protocol.core.RemoteProtocol
import com.techport.tvremote.protocol.mock.MockTvProtocol
import com.techport.tvremote.service.connection.AutoConnector
import com.techport.tvremote.service.connection.ConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, AppSettings())

    val protocols = ProtocolRegistry(
        buildList<RemoteProtocol> {
            if (BuildConfig.DEBUG) add(MockTvProtocol())
        },
    )

    val discovery = DiscoveryService(protocols)
    val haptics = Haptics(context)
    val networkMonitor = NetworkMonitor(context)

    val connectionManager = ConnectionManager(
        scope = connectionScope,
        registry = protocols,
        devices = deviceRepository,
        logger = logger,
        autoReconnect = { settings.value.autoReconnect },
        connectTimeoutMs = { settings.value.connectionTimeoutSec * 1_000L },
    )

    private val autoConnector = AutoConnector(deviceRepository, connectionManager)

    fun start() {
        appScope.launch { autoConnector.connectPreferred() }
        // Wi-Fi came back or changed: retry promptly instead of waiting out backoff.
        networkMonitor.status
            .drop(1)
            .filter { it.isLocalNetworkAvailable }
            .onEach { connectionManager.onNetworkAvailable() }
            .launchIn(appScope)
    }

    /** Forgets a device everywhere: session, credentials and saved record. */
    suspend fun forget(device: TvDevice) {
        connectionManager.release(device.id)
        protocols[device.protocolId]?.forget(device)
        deviceRepository.remove(device.id)
    }
}
