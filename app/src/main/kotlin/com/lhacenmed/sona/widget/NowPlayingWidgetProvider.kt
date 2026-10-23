package com.lhacenmed.sona.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.annotation.IdRes
import com.lhacenmed.sona.BuildConfig
import com.lhacenmed.sona.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The launcher's side of [NowPlayingWidget] - Auxio's `WidgetProvider`: has it draw the widgets as the launcher
 * places or refreshes them, and hands it each press of the widget's buttons.
 */
@AndroidEntryPoint
class NowPlayingWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var widget: NowPlayingWidget

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        widget.start()
    }

    // HyperOS asks its widgets to draw again with an action of its own, which AppWidgetProvider does not know.
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MIUI_APPWIDGET_UPDATE) {
            widget.start()
            return
        }
        WidgetAction.entries.firstOrNull { it.intentAction == intent.action }?.let(widget::perform)
    }
}

/** Xiaomi's own ask for its widgets to be drawn again - see the receiver in the manifest. */
private const val ACTION_MIUI_APPWIDGET_UPDATE = "miui.appwidget.action.APPWIDGET_UPDATE"

/** A button on the widget, by the view it is drawn as - see [NowPlayingWidget.perform] for what each does. */
enum class WidgetAction(@IdRes val viewId: Int) {
    PLAY_PAUSE(R.id.widget_play_pause),
    PREVIOUS(R.id.widget_previous),
    NEXT(R.id.widget_next),
    SHUFFLE(R.id.widget_shuffle),
    REPEAT(R.id.widget_repeat),
    FAVORITE(R.id.widget_favorite),
    ;

    val intentAction = "${BuildConfig.APPLICATION_ID}.widget.action.$name"

    /** The press, sent to [NowPlayingWidgetProvider] alone. */
    fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        ordinal,
        Intent(context, NowPlayingWidgetProvider::class.java).setAction(intentAction),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
