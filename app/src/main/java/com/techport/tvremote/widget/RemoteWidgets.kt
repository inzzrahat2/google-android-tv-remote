package com.techport.tvremote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.techport.tvremote.MainActivity
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.RemoteKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The two widget sizes. Each maps its layout's buttons to remote keys. */
enum class RemoteWidgetKind(
    val layoutRes: Int,
    val provider: Class<out AppWidgetProvider>,
    val buttons: List<Pair<Int, RemoteKey>>,
) {
    SMALL(
        R.layout.widget_remote_small,
        RemoteWidgetSmallProvider::class.java,
        COMMON_BUTTONS,
    ),
    LARGE(
        R.layout.widget_remote_large,
        RemoteWidgetLargeProvider::class.java,
        COMMON_BUTTONS + listOf(
            R.id.widget_btn_up to RemoteKey.DPAD_UP,
            R.id.widget_btn_down to RemoteKey.DPAD_DOWN,
            R.id.widget_btn_left to RemoteKey.DPAD_LEFT,
            R.id.widget_btn_right to RemoteKey.DPAD_RIGHT,
            R.id.widget_btn_ok to RemoteKey.DPAD_CENTER,
        ),
    ),
}

private val COMMON_BUTTONS: List<Pair<Int, RemoteKey>>
    get() = listOf(
        R.id.widget_btn_home to RemoteKey.HOME,
        R.id.widget_btn_back to RemoteKey.BACK,
        R.id.widget_btn_play_pause to RemoteKey.MEDIA_PLAY_PAUSE,
        R.id.widget_btn_vol_down to RemoteKey.VOLUME_DOWN,
        R.id.widget_btn_mute to RemoteKey.VOLUME_MUTE,
        R.id.widget_btn_vol_up to RemoteKey.VOLUME_UP,
    )

/** Builds and pushes widget RemoteViews. No state of its own; safe to call from any thread. */
object RemoteWidgets {

    fun render(context: Context, kind: RemoteWidgetKind, model: WidgetModel): RemoteViews =
        RemoteViews(context.packageName, kind.layoutRes).apply {
            val openApp = openAppIntent(context)
            setTextViewText(R.id.widget_title, model.tvName ?: context.getString(R.string.app_name))
            // The subtitle only appears while there is nothing to control yet.
            setTextViewText(R.id.widget_subtitle, context.getString(R.string.platform_widget_set_up))
            setViewVisibility(R.id.widget_subtitle, if (model.hasTv) View.GONE else View.VISIBLE)
            setOnClickPendingIntent(R.id.widget_header, openApp)
            kind.buttons.forEach { (viewId, key) ->
                // Without a TV a key press can do nothing useful, so every button opens the app.
                setOnClickPendingIntent(viewId, if (model.hasTv) keyIntent(context, key) else openApp)
            }
        }

    /** Re-renders every placed widget of both sizes. Cheap no-op when none are placed. */
    fun updateAll(context: Context, model: WidgetModel) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        RemoteWidgetKind.entries.forEach { kind ->
            val ids = manager.getAppWidgetIds(ComponentName(context, kind.provider)) ?: return@forEach
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context, kind, model))
        }
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun keyIntent(context: Context, key: RemoteKey): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_KEY_BASE + key.ordinal,
        Intent(context, WidgetActionReceiver::class.java)
            .setAction(WidgetActionReceiver.ACTION_KEY)
            .putExtra(WidgetActionReceiver.EXTRA_KEY, key.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val REQUEST_OPEN_APP = 1
    private const val REQUEST_KEY_BASE = 100
}

/**
 * Shared provider logic. The widgets are static RemoteViews: no periodic updates
 * (updatePeriodMillis = 0) and no service. The app refreshes them while it runs
 * ([RemoteWidgetUpdater]); the system asks for a render when one is placed.
 */
abstract class RemoteWidgetProvider(private val kind: RemoteWidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val container = context.tvRemoteContainer
        container.appScope.launch {
            try {
                val state = container.connectionManager.state.value
                val devices = container.deviceRepository
                val target = widgetTarget(state, devices.devices.first(), devices.lastSelectedId.first())
                manager.updateAppWidget(appWidgetIds, RemoteWidgets.render(context, kind, WidgetModel(target?.name)))
            } finally {
                pending.finish()
            }
        }
    }
}

class RemoteWidgetSmallProvider : RemoteWidgetProvider(RemoteWidgetKind.SMALL)

class RemoteWidgetLargeProvider : RemoteWidgetProvider(RemoteWidgetKind.LARGE)
