package com.lhacenmed.sona.core.common.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
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

    /** A notification on [channel], with Sona's icon - where every notification the app posts starts. */
    fun builder(context: Context, channel: SonaNotificationChannel): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_sona)

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
