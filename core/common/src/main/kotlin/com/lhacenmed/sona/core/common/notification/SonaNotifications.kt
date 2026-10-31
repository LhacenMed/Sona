package com.lhacenmed.sona.core.common.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lhacenmed.sona.core.common.R

/**
 * The one way the app posts a notification: every channel made up front, every notification started
 * from the same builder - so each carries Sona's own icon in the status bar - and posted only where
 * Android would show it.
 */
object SonaNotifications {

    /**
     * Creates every [SonaNotificationChannel], or renames one to the current language. Called as the app
     * starts, before anything can post, in a single call to the system.
     */
    fun createChannels(context: Context) {
        val channels = SonaNotificationChannel.entries.map { channel ->
            NotificationChannelCompat.Builder(channel.id, channel.importance)
                .setName(context.getString(channel.nameRes))
                .setDescription(context.getString(channel.descriptionRes))
                // A badge on the launcher is for what asks to be noticed, not for work in progress.
                .setShowBadge(channel.importance >= NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .build()
        }
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels)
    }

    /** Sona's icon, every notification's in the status bar. */
    @DrawableRes
    val icon: Int = R.drawable.ic_stat_sona

    /** A notification on [channel], with Sona's icon - where every notification the app posts starts. */
    fun builder(context: Context, channel: SonaNotificationChannel): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(icon)

    /**
     * [builder], as the platform's own - for what only it has: progress promoted to a live update, on Android 16
     * and later, where [canPostPromoted].
     */
    fun platformBuilder(context: Context, channel: SonaNotificationChannel): Notification.Builder =
        Notification.Builder(context, channel.id)
            .setSmallIcon(icon)

    /** Whether Android shows Sona's progress as live updates - on Android 16 and later, while the user allows it. */
    fun canPostPromoted(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()

    /**
     * Posts [notification] as [id], replacing whatever that id showed - unless notifications are turned
     * off for the app, or not yet allowed, when there is nothing to post to.
     */
    @SuppressLint("MissingPermission") // What areNotificationsEnabled() answers, the permission included.
    fun post(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    /** Takes the notification [id] out of the shade. */
    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}

/**
 * Promotes this service to the foreground, showing [notification] as [id] - a data-sync service, the
 * type every one of Sona's is. `startForegroundService()` requires this within seconds of every start.
 */
fun Service.startForegroundCompat(id: Int, notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        startForeground(id, notification)
    }
}
