package com.lhacenmed.sona.open

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.cover.drawDefaultCover
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.datastore.PlayerStyleSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.player.PlayerElapsedTime
import com.lhacenmed.sona.core.designsystem.player.PlayerEndTime
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.feature.playback.OutsideTrackPlayer
import com.lhacenmed.sona.feature.player.StyledPlaybackSlider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/** How often the seek bar follows the track while it plays. */
private const val PositionTickMillis = 250L

/** The side the cover is read at: as large as the card shows it, blurred or not. */
private const val CoverSizePx = 512

/** The card's own corner, Material's extra large, and its cover's. */
private val CardShape = RoundedCornerShape(28.dp)
private val CoverShape = RoundedCornerShape(16.dp)
private val CoverSize = 88.dp

/** Over the cover's blur, so the card's text reads over any picture. */
private val CoverScrim = Color.Black.copy(alpha = 0.45f)

/** What the popup stands on, so the app under it recedes. */
private val BackdropScrim = Color.Black.copy(alpha = 0.32f)

/** The popup's track, played as long as it is open, and the player's own seek bar style and end time. */
@HiltViewModel
internal class TrackPopupViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val playerStyleSettings: PlayerStyleSettings,
) : ViewModel() {
    val player = OutsideTrackPlayer(context)

    val sliderStyle: StateFlow<PlayerSliderStyle> = playerStyleSettings.appearance.flow
        .map { it.sliderStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playerStyleSettings.appearance.value.sliderStyle)

    val showsTotalDuration: StateFlow<Boolean> = playerStyleSettings.showsTotalDuration.stateIn(viewModelScope)

    fun onToggleEndTime() {
        viewModelScope.launch { playerStyleSettings.setShowsTotalDuration(!showsTotalDuration.value) }
    }

    override fun onCleared() = player.release()
}

/**
 * A track opened from another app, playing in a card over it - Xiaomi Music's popup: its cover over the cover's own blur,
 * its title and artist, play and pause, and a seek bar with its times - the player's own, in the style the user chose.
 * A tap beside the card closes it, as ✕ does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrackPopup(viewModel: TrackPopupViewModel, onClose: () -> Unit) {
    val player = viewModel.player
    val state by player.state.collectAsStateWithLifecycle()
    val sliderStyle by viewModel.sliderStyle.collectAsStateWithLifecycle()
    val showsTotalDuration by viewModel.showsTotalDuration.collectAsStateWithLifecycle()
    val cover = rememberCover(state.artwork)
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackdropScrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.9f) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Color.Black)
                    // Taps on the card stay on it, rather than closing it as the space around it does.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) {
                cover?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Blurred where Android blurs; elsewhere the picture is only darkened further.
                        alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 1f else 0.4f,
                        modifier = Modifier.matchParentSize().blur(48.dp),
                    )
                }
                Box(Modifier.matchParentSize().background(CoverScrim))
                PopupContent(
                    title = state.title,
                    subtitle = if (state.hasFailed) "Can't play this file" else state.artist,
                    cover = cover,
                    isPlaying = state.isPlaying,
                    canPlay = !state.hasFailed,
                    durationMs = state.durationMs,
                    positionMs = { player.positionMs },
                    sliderStyle = sliderStyle,
                    showsTotalDuration = showsTotalDuration,
                    onToggleEndTime = viewModel::onToggleEndTime,
                    onTogglePlayPause = player::togglePlayPause,
                    onSeek = player::seekTo,
                    onClose = onClose,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PopupContent(
    title: String,
    subtitle: String?,
    cover: ImageBitmap?,
    isPlaying: Boolean,
    canPlay: Boolean,
    durationMs: Long,
    positionMs: () -> Long,
    sliderStyle: PlayerSliderStyle,
    showsTotalDuration: Boolean,
    onToggleEndTime: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    // Followed while it plays; held where the finger is while the seek bar is dragged.
    var shownPositionMs by remember { mutableLongStateOf(positionMs()) }
    var draggedFraction by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(isPlaying) {
        while (true) {
            shownPositionMs = positionMs()
            if (!isPlaying) break
            delay(PositionTickMillis)
        }
    }
    val fraction = draggedFraction ?: if (durationMs > 0L) (shownPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val timesPositionMs = draggedFraction?.let { (it * durationMs).toLong() } ?: shownPositionMs

    Column(modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SonaIconButton(onClick = onClose, icon = SonaIcons.Close, contentDescription = "Close", colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            cover?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(CoverSize).clip(CoverShape),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledIconButton(
                onClick = onTogglePlayPause,
                shapes = iconButtonPressShapes(),
                enabled = canPlay,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                ),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) SonaIcons.Pause else SonaIcons.Play,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        StyledPlaybackSlider(
            sliderStyle = sliderStyle,
            value = fraction,
            valueRange = 0f..1f,
            onValueChange = { draggedFraction = it },
            onValueChangeFinished = {
                draggedFraction?.let { dragged ->
                    val target = (dragged * durationMs).toLong()
                    onSeek(target)
                    shownPositionMs = target
                }
                draggedFraction = null
            },
            activeColor = Color.White,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, end = 8.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
            PlayerElapsedTime(timesPositionMs, Color.White, Modifier.align(Alignment.CenterStart))
            PlayerEndTime(
                positionMs = timesPositionMs,
                durationMs = max(durationMs, 0L),
                showsTotalDuration = showsTotalDuration,
                onToggle = onToggleEndTime,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/**
 * The picture embedded in the track, read once at the size the card shows it - or, where it has none, the app's own
 * default cover, as a track without artwork shows everywhere else.
 */
@Composable
private fun rememberCover(artwork: ByteArray?): ImageBitmap? {
    val context = LocalContext.current
    val isNight = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val cover by produceState<ImageBitmap?>(null, artwork, isNight) {
        value = withContext(Dispatchers.Default) {
            val embedded = artwork?.let { bytes ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sampleSize = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= CoverSizePx) sampleSize *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            }
            (embedded ?: drawDefaultCover(context, isNight, CoverSizePx)).asImageBitmap()
        }
    }
    return cover
}
