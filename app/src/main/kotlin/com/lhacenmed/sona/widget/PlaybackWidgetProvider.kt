package com.lhacenmed.sona.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.annotation.IdRes
import com.lhacenmed.sona.BuildConfig
import com.lhacenmed.sona.R
import javax.inject.Inject

/**
 * The launcher's side of [PlaybackWidgets], for every widget it draws - Auxio's `WidgetProvider`: has it draw
 * the widgets as the launcher places or refreshes them, and hands it each press of their buttons. Each widget is
 * one provider of its own, so the launcher lists it apart - see [WidgetKind].
 */
abstract class PlaybackWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var widget: PlaybackWidgets

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

/** Xiaomi's own ask for its widgets to be drawn again - see the receivers in the manifest. */
private const val ACTION_MIUI_APPWIDGET_UPDATE = "miui.appwidget.action.APPWIDGET_UPDATE"

/**
 * A button on the widgets, by the view it is drawn as - see [PlaybackWidgets.perform] for what each does. A widget
 * without one of the views has no such button.
 */
enum class WidgetAction(@IdRes val viewId: Int) {
    PLAY_PAUSE(R.id.widget_play_pause),
    PREVIOUS(R.id.widget_previous),
    NEXT(R.id.widget_next),
    SHUFFLE(R.id.widget_shuffle),
    REPEAT(R.id.widget_repeat),
    FAVORITE(R.id.widget_favorite),
    ;

    val intentAction = "${BuildConfig.APPLICATION_ID}.widget.action.$name"

    /** The press, sent to one provider of the widgets: every one of them hands it on the same. */
    fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        ordinal,
        Intent(context, NowPlayingWidgetProvider::class.java).setAction(intentAction),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
