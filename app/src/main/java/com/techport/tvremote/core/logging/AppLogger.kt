package com.techport.tvremote.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(val timeMillis: Long, val level: LogLevel, val tag: String, val message: String)

/**
 * Structured in-memory log (ring buffer) feeding the debug screen, mirrored to logcat in debug builds.
 *
 * Never pass pairing codes, credentials, clipboard text, typed text or voice data to this logger.
 * Nothing here leaves the device.
 */
class AppLogger(
    private val capacity: Int = 400,
    private val echoToLogcat: Boolean = false,
    private val minLevel: LogLevel = LogLevel.DEBUG,
) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)
    fun w(tag: String, message: String, t: Throwable? = null) = log(LogLevel.WARN, tag, message, t)
    fun e(tag: String, message: String, t: Throwable? = null) = log(LogLevel.ERROR, tag, message, t)

    fun clear() = _entries.update { emptyList() }

    private fun log(level: LogLevel, tag: String, message: String, t: Throwable?) {
        if (level < minLevel) return
        val text = if (t == null) message else "$message (${t.javaClass.simpleName}: ${t.message})"
        _entries.update { (if (it.size >= capacity) it.drop(it.size - capacity + 1) else it) + LogEntry(System.currentTimeMillis(), level, tag, text) }
        if (echoToLogcat) {
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, text)
                LogLevel.INFO -> Log.i(tag, text)
                LogLevel.WARN -> Log.w(tag, text)
                LogLevel.ERROR -> Log.e(tag, text)
            }
        }
    }
}
