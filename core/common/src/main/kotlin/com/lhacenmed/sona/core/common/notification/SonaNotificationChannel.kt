package com.lhacenmed.sona.core.common.notification

import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import com.lhacenmed.sona.core.common.R

/**
 * Every notification channel the app posts to, in one place - the list Android's notification settings
 * show for Sona. Each is created once, as the app starts (see [SonaNotifications.createChannels]), so a
 * feature only names the channel it posts to.
 *
 * The ids are the ones each channel was first created under: a user's choices for a channel are kept
 * against its id, so changing one would reset them.
 */
enum class SonaNotificationChannel(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val importance: Int,
) {
    /** The media notification, whose controls stay in the shade while music plays. */
    PLAYBACK(
        id = "sona_playback_channel",
        nameRes = R.string.notification_channel_playback,
        descriptionRes = R.string.notification_channel_playback_description,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
    ),

    /** A new version is out - the one channel that is meant to be noticed. */
    UPDATE_ALERTS(
        id = "update_notification_channel",
        nameRes = R.string.notification_channel_update_alerts,
        descriptionRes = R.string.notification_channel_update_alerts_description,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
    ),

    /** An update's APK downloading, then ready to install. */
    UPDATE_DOWNLOAD(
        id = "app_update",
        nameRes = R.string.notification_channel_update_download,
        descriptionRes = R.string.notification_channel_update_download_description,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
    ),

    /** A rescan of the device's music, while it runs. */
    LIBRARY_SCAN(
        id = "library_scan",
        nameRes = R.string.notification_channel_library_scan,
        descriptionRes = R.string.notification_channel_library_scan_description,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
    ),
}

/** Every notification's id, in one place so no two features ever post under the same one. */
object SonaNotificationId {
    const val PLAYBACK = 888
    const val UPDATE_DOWNLOAD = 4200
    const val UPDATE_DOWNLOAD_RESULT = 4201
    const val LIBRARY_SCAN = 4300
    const val UPDATE_AVAILABLE = 9999
}
