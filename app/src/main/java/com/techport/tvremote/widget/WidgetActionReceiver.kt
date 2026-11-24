package com.techport.tvremote.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.tvRemoteContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles widget button presses. Not exported: only our own (immutable, explicit)
 * PendingIntents reach it.
 *
 * Uses goAsync() so it can connect if needed (up to ~4 s) and deliver the key, then lets the
 * process go. The session itself is closed by [com.techport.tvremote.service.connection.ConnectionLifecycle]
 * after its background grace period, so nothing is kept alive here.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEY) return
        val key = intent.getStringExtra(EXTRA_KEY)?.let { name -> RemoteKey.entries.find { it.name == name } } ?: return
        val container = context.tvRemoteContainer
        val sender = WidgetKeySender(
            controller = container.connectionManager,
            devices = container.deviceRepository,
            connect = container.connectionLifecycle::connectForBackgroundUse,
            onUsed = container.connectionLifecycle::noteBackgroundUse,
        )
        val pending = goAsync()
        container.appScope.launch {
            try {
                // Stay well inside the broadcast time limit.
                val outcome = withTimeoutOrNull(RECEIVER_BUDGET_MS) { sender.send(key) }
                    ?: WidgetKeyOutcome(WidgetKeyResult.UNREACHABLE, null)
                if (outcome.result == WidgetKeyResult.SENT) {
                    // send() only queues the key; give the connection's writer a moment to flush it
                    // before the process may be frozen.
                    delay(FLUSH_MS)
                } else {
                    container.logger.i(TAG, "Widget ${key.name}: ${outcome.result}")
                    feedback(context.applicationContext, outcome)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun feedback(context: Context, outcome: WidgetKeyOutcome) {
        val name = outcome.device?.name ?: context.getString(R.string.widget_your_tv)
        val text = when (outcome.result) {
            WidgetKeyResult.SENT -> return
            WidgetKeyResult.NO_TV -> context.getString(R.string.widget_no_tv)
            WidgetKeyResult.NEEDS_PAIRING -> context.getString(R.string.widget_needs_pairing, name)
            WidgetKeyResult.UNREACHABLE -> context.getString(R.string.widget_unreachable, name)
            WidgetKeyResult.UNSUPPORTED -> context.getString(R.string.widget_unsupported, name)
        }
        withContext(Dispatchers.Main) { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val ACTION_KEY = "com.techport.tvremote.widget.action.KEY"
        const val EXTRA_KEY = "key"
        private const val RECEIVER_BUDGET_MS = 7_000L
        private const val FLUSH_MS = 300L
        private const val TAG = "Widget"
    }
}
