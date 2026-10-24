package com.techport.tvremote.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.core.logging.LogEntry
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.service.connection.ConnectionManager
import com.techport.tvremote.service.connection.ConnectionMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DebugViewModel(
    private val manager: ConnectionManager,
    private val logger: AppLogger,
) : ViewModel() {
    val state: StateFlow<ConnectionState> = manager.state
    val metrics: StateFlow<ConnectionMetrics> = manager.metrics
    val logs: StateFlow<List<LogEntry>> = logger.entries

    private val _latency = MutableStateFlow<Long?>(null)
    val latency: StateFlow<Long?> = _latency.asStateFlow()

    fun measure() = viewModelScope.launch { _latency.value = manager.measureLatency() }
    fun reconnect() = manager.retry()
    fun disconnect() = manager.disconnect()
    fun sendTest() = manager.send(RemoteCommand.Key(RemoteKey.DPAD_CENTER))
    fun clearLogs() = logger.clear()
}
