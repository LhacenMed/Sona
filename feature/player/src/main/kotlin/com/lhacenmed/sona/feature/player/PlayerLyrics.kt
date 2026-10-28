package com.lhacenmed.sona.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.theme.roundedRadius
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.core.model.Track
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** How long the player takes to open into its lyrics; closing runs the same timeline back, a little quicker. */
private const val LyricsOpenDurationMs = 520
private const val LyricsCloseDurationMs = 430

/**
 * The player's lyrics: one line of them in a pill above the seek bar, which grows into the full lyrics over the
 * player and shrinks back into the line - the same surface throughout, never a sheet of its own.
 *
 * A player style places the pill - [LyricsPillSlot] - marks where the open lyrics go - [lyricsPane] - and moves its
 * own elements out of their way along [progress], each over its own part of it - [phase]. [PlayerLyrics] draws the
 * pill and what it grows into above the style.
 */
@Stable
internal class PlayerLyricsState(
    isOpen: Boolean,
    showsPlayerControlsSetting: State<Boolean>,
) {
    var isOpen by mutableStateOf(isOpen)
        private set

    /** 0 in the player, 1 in its lyrics - at an even pace, each element easing its own part of it. */
    val progress = Animatable(if (isOpen) 1f else 0f)

    /** Whether the seek bar and transport stay beneath the open lyrics, or give them their room. */
    val showsPlayerControls by showsPlayerControlsSetting

    var isMenuShown by mutableStateOf(false)
        private set

    /** Whether the lyrics are open, or still on their way in or out. */
    val isRevealed by derivedStateOf { this.isOpen || progress.value > 0f }

    internal var stage: LayoutCoordinates? = null
    internal var pillBounds by mutableStateOf(Rect.Zero)
    internal var paneBounds by mutableStateOf(Rect.Zero)

    /** How far past [lyricsPane]'s bottom the open lyrics reach - into the room the style's elements leave them. */
    internal var paneExtensionPx by mutableFloatStateOf(0f)

    fun open() {
        isOpen = true
    }

    fun close() {
        isOpen = false
        isMenuShown = false
    }

    fun showMenu() {
        isMenuShown = true
    }

    fun hideMenu() {
        isMenuShown = false
    }

    /** How far, eased out, an element moving over [start]..[end] of [progress] has come. Read where it is drawn. */
    fun phase(start: Float, end: Float): Float {
        val linear = ((progress.value - start) / (end - start)).coerceIn(0f, 1f)
        return 1f - (1f - linear).pow(3)
    }

    internal fun boundsOf(coordinates: LayoutCoordinates): Rect? =
        stage?.takeIf { it.isAttached }?.localBoundingBoxOf(coordinates)
}

/** Open or closed as the player was left, across recreation; at rest, never mid-way. */
@Composable
internal fun rememberPlayerLyricsState(lyricsViewModel: LyricsViewModel): PlayerLyricsState {
    val showsPlayerControls = lyricsViewModel.showLyricsPlayerControls.collectAsStateWithLifecycle()
    val state =
        rememberSaveable(
            saver = Saver(save = { it.isOpen }, restore = { PlayerLyricsState(it, showsPlayerControls) }),
        ) { PlayerLyricsState(false, showsPlayerControls) }
    LaunchedEffect(state, state.isOpen) {
        val target = if (state.isOpen) 1f else 0f
        val fullDurationMs = if (state.isOpen) LyricsOpenDurationMs else LyricsCloseDurationMs
        // Turned back part-way, it takes only as long as the way it has come.
        val durationMs = (abs(target - state.progress.value) * fullDurationMs).roundToInt()
        state.progress.animateTo(target, tween(durationMs, easing = LinearEasing))
    }
    return state
}

/** Where the pill sits in a player style, at rest: the room it takes, drawn by [PlayerLyrics]. */
@Composable
internal fun LyricsPillSlot(
    state: PlayerLyricsState,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(PlayerPillHeight)
            .onGloballyPositioned { coordinates -> state.boundsOf(coordinates)?.let { state.pillBounds = it } },
    )
}

/** Marks the room the open lyrics take in a player style; the style moves what is drawn there out of the way. */
internal fun Modifier.lyricsPane(state: PlayerLyricsState): Modifier =
    onGloballyPositioned { coordinates -> state.boundsOf(coordinates)?.let { state.paneBounds = it } }

/**
 * The pill and the lyrics it grows into, laid over the player style that placed them, so the line in the pill
 * and the lines it opens onto are one surface. The open lyrics are laid out once, where they end up, and
 * revealed as the pill grows over them, so nothing is measured again while it moves.
 *
 * Back closes them; their menu offers, beside what is about the lyrics, what the player puts away while they
 * are open - [onToggleFavorite], [onOpenQueue] and [onSleepTimerClick].
 */
@Composable
internal fun PlayerLyrics(
    state: PlayerLyricsState,
    track: Track,
    durationMs: Long,
    contentColor: Color,
    /** Where playback is this moment, followed by the open lyrics as they fill. */
    positionMsProvider: () -> Long,
    sliderPositionProvider: () -> Long?,
    /** Where the seek bar shows playback - the pill's line follows it, changing only as the line does. */
    seekBarPositionMs: () -> Long,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onOpenQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onEditLyrics: ((trackId: Long) -> Unit)?,
    lyricsViewModel: LyricsViewModel,
    modifier: Modifier = Modifier,
) {
    // Null while the file is being read. Keyed by the whole track, so it is read again as the library reads it anew.
    val lyrics by remember(track) { lyricsViewModel.lyrics(track) }.collectAsStateWithLifecycle(initialValue = null)
    val isFindingLyrics by remember(track) { lyricsViewModel.isFindingLyrics(track) }.collectAsStateWithLifecycle(initialValue = false)
    val timeline = remember(lyrics, durationMs) { lyrics?.let { LyricsTimeline.parse(it, durationMs) } }

    BackHandler(enabled = state.isOpen, onBack = state::close)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onPlaced { state.stage = it },
    ) {
        val pill = state.pillBounds
        if (pill.isEmpty) return@Box
        val pane = state.paneBounds.let { it.copy(bottom = it.bottom + state.paneExtensionPx) }
        val density = LocalDensity.current
        val pillCornerRadius = roundedRadius(PlayerPillCornerRadius)
        val tint = contentColor.copy(alpha = PlayerPillTintAlpha)

        // The pill's own fill, growing with it and fading into the player's background as it opens.
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val grow = state.phase(0.04f, 0.88f)
                    val fill = 1f - state.phase(0.3f, 0.9f)
                    if (fill <= 0f) return@drawBehind
                    val bounds = lerp(pill, pane, grow)
                    drawRoundRect(
                        color = tint.copy(alpha = tint.alpha * fill),
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        cornerRadius = CornerRadius(lerp(pillCornerRadius.toPx(), 0f, grow)),
                    )
                },
        )

        Box(
            modifier =
                Modifier
                    .offset { pill.topLeft.round() }
                    .size(with(density) { pill.size.toDpSize() })
                    .graphicsLayer { alpha = 1f - state.phase(0f, 0.22f) }
                    .clip(roundedShape(PlayerPillCornerRadius))
                    .clickable(
                        enabled = !state.isOpen,
                        onClickLabel = stringResource(R.string.player_lyrics),
                        onClick = state::open,
                    ),
        ) {
            LyricsPillLine(
                timeline = timeline,
                isFindingLyrics = isFindingLyrics,
                seekBarPositionMs = seekBarPositionMs,
                lyricsSyncOffset = lyricsSyncOffset,
                color = contentColor,
            )
        }

        if (state.isRevealed && !pane.isEmpty) {
            Box(
                modifier =
                    Modifier
                        .offset { pane.topLeft.round() }
                        .size(with(density) { pane.size.toDpSize() })
                        .graphicsLayer {
                            val grow = state.phase(0.04f, 0.88f)
                            val revealed = lerp(pill, pane, grow).translate(-pane.left, -pane.top)
                            val cornerRadius = CornerRadius(lerp(pillCornerRadius.toPx(), 0f, grow))
                            shape = GenericShape { _, _ -> addRoundRect(RoundRect(revealed, cornerRadius)) }
                            clip = true
                            alpha = state.phase(0.2f, 1f)
                        }.consumeUnhandledPointerInput(),
            ) {
                LyricsV2(
                    track = track,
                    timeline = timeline,
                    isFindingLyrics = isFindingLyrics,
                    positionMsProvider = positionMsProvider,
                    sliderPositionProvider = sliderPositionProvider,
                    lyricsSyncOffset = lyricsSyncOffset,
                    onSeek = onSeek,
                    textColor = contentColor,
                    lyricsViewModel = lyricsViewModel,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp),
                )
            }
        }
    }

    if (state.isMenuShown) {
        LyricsMenuSheet(
            track = track,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onOpenQueue = onOpenQueue,
            onSleepTimerClick = onSleepTimerClick,
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
            showPlayerControls = state.showsPlayerControls,
            onShowPlayerControlsChange = lyricsViewModel::setShowLyricsPlayerControls,
            onEditLyrics = onEditLyrics?.let { editLyrics -> { editLyrics(track.id) } },
            onDismissRequest = state::hideMenu,
        )
    }
}

/** A line for the pill; [isUpcoming] while nothing is sung, showing the line to come. */
private data class LyricsPillText(val text: String, val isUpcoming: Boolean)

/**
 * The line playing, sliding up as the next takes its place - or, before the first line and through a break,
 * the line to come, faint. Plain lyrics show their first line; none yet, what the pill opens.
 */
@Composable
private fun LyricsPillLine(
    timeline: LyricsTimeline?,
    isFindingLyrics: Boolean,
    seekBarPositionMs: () -> Long,
    lyricsSyncOffset: Int,
    color: Color,
) {
    val latestSeekBarPositionMs by rememberUpdatedState(seekBarPositionMs)
    val line by remember(timeline, lyricsSyncOffset) {
        derivedStateOf { timeline?.pillText(latestSeekBarPositionMs(), lyricsSyncOffset) }
    }
    val shown =
        line ?: LyricsPillText(
            text = stringResource(if (isFindingLyrics) R.string.player_lyrics_finding else R.string.player_lyrics),
            isUpcoming = false,
        )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.lyrics),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            contentAlignment = Alignment.CenterStart,
            label = "lyricsPillLine",
            modifier = Modifier.weight(1f),
        ) { text ->
            Text(
                text = text.text,
                color = color.copy(alpha = if (text.isUpcoming) 0.5f else 1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LyricsTimeline.pillText(seekBarPositionMs: Long, syncOffsetMs: Int): LyricsPillText? {
    if (entries.isEmpty()) return null
    if (!isSynced) return LyricsPillText(entries.first().text, isUpcoming = false)
    val index = lineIndexAt(lyricsPositionMs(seekBarPositionMs, syncOffsetMs))
    val isSung: (Int) -> Boolean = { entries[it] != HEAD_LYRICS_ENTRY && !entries[it].isInstrumental && entries[it].text.isNotBlank() }
    if (isSung(index)) return LyricsPillText(entries[index].text, isUpcoming = false)
    val next = (index + 1..entries.lastIndex).firstOrNull(isSung) ?: return null
    return LyricsPillText(entries[next].text, isUpcoming = true)
}

/** Keeps touches from falling through the open lyrics to the player beneath them. */
private fun Modifier.consumeUnhandledPointerInput(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Final).changes.forEach { change ->
                    if (!change.isConsumed) change.consume()
                }
            }
        }
    }
