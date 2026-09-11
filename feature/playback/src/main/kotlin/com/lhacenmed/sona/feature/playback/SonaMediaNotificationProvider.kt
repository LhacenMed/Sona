package com.lhacenmed.sona.feature.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * The media notification, ported from ArchiveTune's `ArchiveTuneMediaNotificationProvider`.
 *
 * It builds nothing itself: media3's [DefaultMediaNotificationProvider] renders the notification
 * from the session, including its custom layout buttons, and this only wraps it to do two things
 * the default cannot. It names the channel and small icon, which is why the status bar shows
 * Sona's mark rather than media3's generic glyph. And it replaces the notification's delete intent
 * so that swiping the notification away arrives at [PlaybackService] as an ordinary command,
 * carrying the original intent along so media3's own dismissal still runs afterwards.
 *
 * Deviation from the source: ArchiveTune also overrides `getNotificationChannelInfo()`. That member
 * does not exist on `MediaNotification.Provider` in media3 1.9.1 (it arrives later), so the override
 * is dropped - the delegate still creates the channel, so behaviour is unchanged.
 */
@UnstableApi
class SonaMediaNotificationProvider(
    private val context: Context,
    @DrawableRes smallIconResId: Int,
) : MediaNotification.Provider {
    private val delegate =
        DefaultMediaNotificationProvider(
            context,
            { PlaybackService.NOTIFICATION_ID },
            PlaybackService.CHANNEL_ID,
            R.string.playback_notification_channel_name,
        ).apply {
            setSmallIcon(smallIconResId)
        }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val mediaNotification =
            delegate.createNotification(
                mediaSession,
                mediaButtonPreferences,
                actionFactory,
                onNotificationChangedCallback,
            )

        val originalDeleteIntent = mediaNotification.notification.deleteIntent ?: return mediaNotification
        mediaNotification.notification.deleteIntent =
            PendingIntent.getService(
                context,
                mediaNotification.notificationId,
                Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_MEDIA_NOTIFICATION_DISMISSED
                    putExtra(
                        PlaybackService.EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT,
                        originalDeleteIntent,
                    )
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return mediaNotification
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)
}
