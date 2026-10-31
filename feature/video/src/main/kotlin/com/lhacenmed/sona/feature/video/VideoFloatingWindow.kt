package com.lhacenmed.sona.feature.video

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** The widest and the tallest a floating window may be, as Android allows. */
private val WidestFloatingWindow = Rational(239, 100)
private val TallestFloatingWindow = Rational(100, 239)

/** Its shape before the picture's is known. */
private val DefaultFloatingWindow = Rational(16, 9)

/**
 * The player's floating window, as the picture and the playback stand: shaped as the picture, with play and
 * pause on it - and, while [canFloat], gone into by itself as the user leaves the player for the rest of the app.
 */
internal data class FloatingWindow(val videoWidth: Int, val videoHeight: Int, val isPlaying: Boolean, val canFloat: Boolean)

/** Whether this device has floating windows at all - some, such as many TVs and low-memory ones, have none. */
internal fun Context.hasFloatingWindows(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

internal fun Context.floatingWindowParams(window: FloatingWindow?): PictureInPictureParams =
    PictureInPictureParams.Builder()
        .setAspectRatio(window?.aspectRatio ?: DefaultFloatingWindow)
        .apply {
            window?.let { setActions(listOf(playPauseAction(it.isPlaying))) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(window?.canFloat == true)
                setSeamlessResizeEnabled(true)
            }
        }
        .build()

private val FloatingWindow.aspectRatio: Rational
    get() {
        if (videoWidth <= 0 || videoHeight <= 0) return DefaultFloatingWindow
        val ratio = Rational(videoWidth, videoHeight)
        return when {
            ratio > WidestFloatingWindow -> WidestFloatingWindow
            ratio < TallestFloatingWindow -> TallestFloatingWindow
            else -> ratio
        }
    }

private fun Context.playPauseAction(isPlaying: Boolean): RemoteAction {
    val label = getString(if (isPlaying) R.string.video_pause else R.string.video_play)
    return RemoteAction(
        Icon.createWithResource(this, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
        label,
        label,
        PendingIntent.getBroadcast(this, 0, Intent(this, VideoFloatingWindowReceiver::class.java), PendingIntent.FLAG_IMMUTABLE),
    )
}

/** Play and pause, pressed on the floating window. */
class VideoFloatingWindowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EntryPointAccessors.fromApplication(context, VideoFloatingWindowEntryPoint::class.java).playbackController().togglePlayPause()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface VideoFloatingWindowEntryPoint {
    fun playbackController(): PlaybackController
}
