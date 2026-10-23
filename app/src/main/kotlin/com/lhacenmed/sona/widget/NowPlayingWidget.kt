package com.lhacenmed.sona.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.PowerManager
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.IntentCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import com.lhacenmed.sona.BuildConfig
import com.lhacenmed.sona.MainActivity
import com.lhacenmed.sona.R
import com.lhacenmed.sona.core.common.cover.drawDefaultCover
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.format.formatDuration
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.datastore.CoverMode
import com.lhacenmed.sona.core.datastore.ImageSettings
import com.lhacenmed.sona.core.designsystem.theme.extractThemeColor
import com.lhacenmed.sona.core.designsystem.theme.materialKolorDynamicColorScheme
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.navigation.ScreenArrival
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackSpace
import com.lhacenmed.sona.feature.playback.VAULT_DISPLAY_TITLE
import com.lhacenmed.sona.feature.playback.iconRes
import com.lhacenmed.sona.feature.playback.labelRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lhacenmed.sona.feature.playback.R as PlaybackR
import com.lhacenmed.sona.feature.player.R as PlayerR

/** The side a cover is loaded at: as sharp as the widget ever shows it, and well within what a widget may hold. */
private const val CoverSizePx = 512

/** How round the cover's corners are, as a share of its side. */
private const val CoverCornerFraction = 0.07f

/** The timeline's level once played to its end - see `widget_progress`. */
private const val MaxTimelineLevel = 10_000

/** The action the widget opens the app with, carrying what the app does as it opens - see [ScreenArrival]. */
private const val ACTION_ARRIVE = "${BuildConfig.APPLICATION_ID}.widget.action.ARRIVE"
private const val EXTRA_ARRIVAL = "${BuildConfig.APPLICATION_ID}.widget.extra.ARRIVAL"

/**
 * The home screen's "Now Playing" widget, kept in step with playback - Auxio's `WidgetComponent` with its
 * `WidgetProvider`'s layouts: the playing track's cover, title and artist, its timeline and the player's
 * controls, all in the colours of its cover.
 *
 * Drawn from what the app itself shows: the playback state every screen reads, the library's tracks and
 * favorites, and whether covers are shown. The colours are the scheme the theme grows from the cover - see
 * [extractThemeColor] - in light or dark as the system is; a track without one is drawn in the default
 * cover's. The Private Folder's media is named as the notification names it, and shows no cover.
 *
 * Two kinds of draw, because a widget is a picture sent to the launcher rather than a view: the whole widget,
 * cover included, whenever anything on it but the timeline changes; and the timeline alone each second it
 * moves by, while the screen is on - a few numbers rather than a cover each time.
 *
 * Tapping it opens the app on the list the queue plays from, its playing row brought into view; its ⋮ opens
 * the player over that list - see [arrivalOf].
 */
@Singleton
class NowPlayingWidget @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val playbackController: PlaybackController,
    private val repository: LibraryRepository,
    private val imageSettings: ImageSettings,
) {
    private val widgetManager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, NowPlayingWidgetProvider::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    private val isStarted = AtomicBoolean(false)
    private val isNight = MutableStateFlow(context.resources.configuration.isNight)

    /** Counts the launcher's asks for the widgets to be drawn again - one just placed among them. */
    private val redraws = MutableStateFlow(0)

    /** The last cover drawn, kept so a press of play does not load it again. */
    private var artwork: Artwork? = null

    /** Whether the widget is on the home screen at all. */
    val isPlaced: Boolean
        get() = widgetManager.getAppWidgetIds(component).isNotEmpty()

    /** Follows playback from now on, drawing every widget placed; asked again, draws them all once more. */
    fun start() {
        if (!isStarted.compareAndSet(false, true)) {
            redraws.update { it + 1 }
            return
        }
        context.registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    isNight.value = newConfig.isNight
                }

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = Unit
            },
        )
        scope.launch { widgetContent().collectLatest(::drawWidget) }
        scope.launch { timeline().collect(::drawTimeline) }
    }

    /**
     * Does what a press of the widget's [action] asks, once the queue is known - so a press that started the
     * app is not lost to the moment before the player is reached.
     */
    fun perform(action: WidgetAction) {
        scope.launch(Dispatchers.Main) {
            val playback = playbackController.playbackState.first { it.isReady }
            when (action) {
                WidgetAction.PLAY_PAUSE -> playbackController.togglePlayPause()
                WidgetAction.PREVIOUS -> playbackController.skipToPrevious()
                WidgetAction.NEXT -> playbackController.skipToNext()
                WidgetAction.SHUFFLE -> playbackController.setShuffleEnabled(!playback.shuffleEnabled)
                WidgetAction.REPEAT -> playbackController.cycleRepeatMode()
                WidgetAction.FAVORITE -> playback.libraryTrackId?.let { trackId ->
                    repository.setFavorite(trackId, trackId !in repository.favoriteTrackIds.value)
                }
            }
        }
    }

    private fun widgetContent(): Flow<WidgetContent> = combine(
        playbackController.playbackState,
        repository.tracksById,
        repository.favoriteTrackIds,
        imageSettings.coverMode.flow,
        combine(isNight, redraws, ::Pair),
    ) { playback, tracksById, favoriteTrackIds, coverMode, (isNight, redraw) ->
        val trackId = playback.libraryTrackId
        WidgetContent(
            track = trackId?.let(tracksById::get),
            isPrivate = playback.space == PlaybackSpace.Private,
            isPlaying = playback.isPlaying,
            isShuffled = playback.shuffleEnabled,
            repeatMode = playback.repeatMode,
            isFavorite = trackId != null && trackId in favoriteTrackIds,
            showsCover = coverMode != CoverMode.OFF,
            isNight = isNight,
            redraw = redraw,
        )
    }.distinctUntilChanged()

    private fun timeline(): Flow<Timeline> = playbackController.playbackState
        .map { Timeline(elapsedSeconds = it.positionMs / 1000, durationSeconds = it.durationMs / 1000) }
        .distinctUntilChanged()

    private suspend fun drawWidget(content: WidgetContent) {
        if (!isPlaced) return
        val artwork = artworkFor(content)
        val playback = playbackController.playbackState.value
        val views = RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
            bindContent(content, artwork)
            bindTimeline(Timeline(playback.positionMs / 1000, playback.durationMs / 1000))
        }
        widgetManager.updateAppWidget(component, views)
    }

    private fun drawTimeline(timeline: Timeline) {
        if (!powerManager.isInteractive) return
        val ids = widgetManager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_now_playing).apply { bindTimeline(timeline) }
        widgetManager.partiallyUpdateAppWidget(ids, views)
    }

    /** [content]'s cover and the colours grown from it - the last ones again while they are the same cover's. */
    private suspend fun artworkFor(content: WidgetContent): Artwork {
        val coverArtUri = content.track?.coverArtUri
            ?.takeIf { it.isNotEmpty() && content.showsCover && !content.isPrivate }
        val key = ArtworkKey(coverArtUri, content.isNight)
        artwork?.takeIf { it.key == key }?.let { return it }
        return withContext(Dispatchers.Default) {
            val source = coverArtUri?.let { loadCover(it) } ?: drawDefaultCover(context, content.isNight, CoverSizePx)
            val scheme = materialKolorDynamicColorScheme(source.extractThemeColor(), content.isNight)
            Artwork(key, source.toWidgetCover(), WidgetColors(scheme))
        }.also { artwork = it }
    }

    private suspend fun loadCover(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(CoverSizePx)
            .scale(Scale.FILL)
            // Read for its colours, and sent to the launcher - neither of which a hardware bitmap allows.
            .allowHardware(false)
            .build()
        (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    private fun RemoteViews.bindContent(content: WidgetContent, artwork: Artwork) {
        val colors = artwork.colors
        val track = content.track
        setInt(R.id.widget_card, "setColorFilter", colors.card)
        setImageViewBitmap(R.id.widget_cover, artwork.cover)

        setTextViewText(
            R.id.widget_title,
            when {
                content.isPrivate -> VAULT_DISPLAY_TITLE
                track != null -> track.title
                else -> context.getString(R.string.widget_nothing_playing)
            },
        )
        setTextViewText(R.id.widget_artist, track?.artist.orEmpty())
        setTextColor(R.id.widget_title, colors.title)
        setTextColor(R.id.widget_artist, colors.subtitle)
        setTextColor(R.id.widget_elapsed, colors.subtitle)
        setTextColor(R.id.widget_duration, colors.subtitle)

        // The same icons and names the notification's buttons have, for the same states.
        setImageViewResource(
            R.id.widget_favorite,
            if (content.isFavorite) PlaybackR.drawable.favorite else PlaybackR.drawable.favorite_border,
        )
        setContentDescription(
            R.id.widget_favorite,
            context.getString(
                if (content.isFavorite) PlaybackR.string.playback_action_remove_favorite else PlaybackR.string.playback_action_add_favorite,
            ),
        )
        setImageViewResource(R.id.widget_shuffle, if (content.isShuffled) PlaybackR.drawable.shuffle_on else PlaybackR.drawable.shuffle)
        setContentDescription(
            R.id.widget_shuffle,
            context.getString(if (content.isShuffled) PlaybackR.string.playback_action_shuffle_off else PlaybackR.string.playback_action_shuffle_on),
        )
        setImageViewResource(R.id.widget_repeat, content.repeatMode.iconRes)
        setContentDescription(R.id.widget_repeat, context.getString(content.repeatMode.labelRes))
        setImageViewResource(R.id.widget_play_pause_icon, if (content.isPlaying) PlayerR.drawable.pause else PlayerR.drawable.play)
        setContentDescription(
            R.id.widget_play_pause,
            context.getString(if (content.isPlaying) PlayerR.string.player_pause else PlayerR.string.player_play),
        )

        for (icon in TintedIcons) setInt(icon, "setColorFilter", colors.icon)
        setInt(R.id.widget_play_pause_circle, "setColorFilter", colors.playButton)
        setInt(R.id.widget_play_pause_icon, "setColorFilter", colors.onPlayButton)
        setInt(R.id.widget_progress, "setColorFilter", colors.timeline)
        setInt(R.id.widget_progress_track, "setColorFilter", colors.timelineTrack)

        setOnClickPendingIntent(android.R.id.background, openApp(content.isPrivate, ScreenArrival.RevealPlaying))
        setOnClickPendingIntent(R.id.widget_open_player, openApp(content.isPrivate, ScreenArrival.ExpandPlayer))
        for (action in WidgetAction.entries) setOnClickPendingIntent(action.viewId, action.pendingIntent(context))
    }

    private fun RemoteViews.bindTimeline(timeline: Timeline) {
        setTextViewText(R.id.widget_elapsed, formatDuration(timeline.elapsedSeconds * 1000))
        setTextViewText(R.id.widget_duration, formatDuration(timeline.durationSeconds * 1000))
        val played = if (timeline.durationSeconds > 0) timeline.elapsedSeconds.toFloat() / timeline.durationSeconds else 0f
        // Never 0: a scaled drawable at level 0 is not drawn at all, and the dot would vanish at the start.
        setInt(R.id.widget_progress, "setImageLevel", (played.coerceIn(0f, 1f) * MaxTimelineLevel).toInt().coerceAtLeast(1))
    }

    /**
     * The app opened from the widget, to do [arrival] on the list the queue plays from - in a task of its own,
     * as the launcher's quick play shortcut opens it, so it opens on that list whatever screen it was left on.
     * While the Private Folder plays, the app is only brought back as it was left: nothing of the folder is
     * named outside it, and its screens stay open.
     */
    private fun openApp(isPrivate: Boolean, arrival: ScreenArrival): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        if (isPrivate) {
            intent.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            intent.setAction(ACTION_ARRIVE)
                .putExtra(EXTRA_ARRIVAL, arrival)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        return PendingIntent.getActivity(
            context,
            arrival.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** What [intent] asks the app to do as it opens, when it is the widget's - see [ScreenArrival]. */
        fun arrivalOf(intent: Intent): ScreenArrival? =
            if (intent.action == ACTION_ARRIVE) IntentCompat.getSerializableExtra(intent, EXTRA_ARRIVAL, ScreenArrival::class.java) else null
    }
}

/** Everything the widget shows but its timeline - see [NowPlayingWidget]. */
private data class WidgetContent(
    /** The library's track playing - null while the Private Folder plays, or nothing does. */
    val track: Track?,
    val isPrivate: Boolean,
    val isPlaying: Boolean,
    val isShuffled: Boolean,
    val repeatMode: RepeatMode,
    val isFavorite: Boolean,
    val showsCover: Boolean,
    val isNight: Boolean,
    /** Changes with each ask to draw the widgets again, so the same content is drawn once more. */
    val redraw: Int,
)

/** The timeline, to the second it shows. */
private data class Timeline(val elapsedSeconds: Long, val durationSeconds: Long)

/** What a cover is drawn for: the same cover in the other mode grows other colours. */
private data class ArtworkKey(val coverArtUri: String?, val isNight: Boolean)

private class Artwork(val key: ArtworkKey, val cover: Bitmap, val colors: WidgetColors)

/** What the widget is drawn in, taken from the scheme its cover grows - its roles where the app's player has them. */
private class WidgetColors(scheme: ColorScheme) {
    val card = scheme.surface.toArgb()
    val title = scheme.onSurface.toArgb()
    val subtitle = scheme.onSurfaceVariant.toArgb()
    val icon = scheme.onSurface.toArgb()
    val playButton = scheme.primary.toArgb()
    val onPlayButton = scheme.onPrimary.toArgb()
    val timeline = scheme.onSurface.toArgb()
    val timelineTrack = scheme.outlineVariant.toArgb()
}

/** The icons drawn in the plain icon colour. */
private val TintedIcons = intArrayOf(
    R.id.widget_favorite,
    R.id.widget_open_player,
    R.id.widget_shuffle,
    R.id.widget_previous,
    R.id.widget_next,
    R.id.widget_repeat,
)

private val Configuration.isNight: Boolean
    get() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** This cut to the square at its centre, its corners rounded - the cover as the widget shows it. */
private fun Bitmap.toWidgetCover(): Bitmap {
    val side = minOf(width, height)
    val cover = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val shader = BitmapShader(this, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setTranslate(-(width - side) / 2f, -(height - side) / 2f) })
    }
    val radius = side * CoverCornerFraction
    Canvas(cover).drawRoundRect(
        0f,
        0f,
        side.toFloat(),
        side.toFloat(),
        radius,
        radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader },
    )
    return cover
}
