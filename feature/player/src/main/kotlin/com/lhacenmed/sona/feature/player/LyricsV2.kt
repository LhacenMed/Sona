@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.lyrics.LyricsEntry
import com.lhacenmed.sona.core.data.lyrics.LyricsRomanizationPreferences
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.findCurrentLineIndex
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.insertInstrumentalBreaks
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.isLineSyncedLrc
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.isTtml
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.parseLyrics
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.parseTtml
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.providedRomanizedTextForEntry
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.romanizeLyricsLine
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils.shouldRomanizeLyricsLine
import com.lhacenmed.sona.core.data.lyrics.WordTimestamp
import com.lhacenmed.sona.core.database.entity.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.lhacenmed.sona.core.designsystem.component.shimmer
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.model.Track
import java.text.BreakIterator
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ──────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────

/** Lead time offset for LRC-style line-synced lyrics (ms). */
private const val LRC_LEAD_MS = 300L

/** Lead time offset for TTML word-synced lyrics (ms). */
private const val TTML_LEAD_MS = 0L

private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L

/** Seconds to wait before auto-scroll resumes after manual scroll. */
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L

/** How many lines can be selected to share at once. */
private const val MAX_SELECTION_LIMIT = 5

/** Sentinel entry prepended so auto-scroll has headroom above the first line. */
private val HEAD_LYRICS_ENTRY = LyricsEntry(time = 0L, text = "")

private fun isRtlText(text: String): Boolean {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
            -> return true

            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
            -> return false
        }
    }
    return false
}

// ──────────────────────────────────────────────────────────────────────
// Main Composable
// ──────────────────────────────────────────────────────────────────────

/**
 * Synced lyrics with a liquid word fill, glow and bounce - ArchiveTune's `LyricsV2`.
 *
 * [lyrics] is null until the track's lyrics have been looked for, and [LYRICS_NOT_FOUND] once they
 * were looked for and there were none.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun LyricsV2(
    track: Track,
    lyrics: String?,
    durationMs: Long,
    positionMsProvider: () -> Long,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    onSeek: (Long) -> Unit,
    textColor: Color,
    lyricsViewModel: LyricsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Preferences ──
    val lyricsClick by lyricsViewModel.lyricsClick.collectAsStateWithLifecycle()
    val lyricsScroll by lyricsViewModel.lyricsScroll.collectAsStateWithLifecycle()
    val lyricsTextSize by lyricsViewModel.lyricsTextSize.collectAsStateWithLifecycle()
    val lyricsLineSpacing by lyricsViewModel.lyricsLineSpacing.collectAsStateWithLifecycle()
    val lyricsLineBlur by lyricsViewModel.lyricsLineBlur.collectAsStateWithLifecycle()
    val bounceFactor by lyricsViewModel.bounceFactor.collectAsStateWithLifecycle()
    val glowFactor by lyricsViewModel.glowFactor.collectAsStateWithLifecycle()
    val fillTransitionWidth by lyricsViewModel.fillTransitionWidth.collectAsStateWithLifecycle()
    val lrcBounceEnabled by lyricsViewModel.lrcBounceEnabled.collectAsStateWithLifecycle()
    val romanizeChinese by lyricsViewModel.romanizeChinese.collectAsStateWithLifecycle()
    val romanizeHindi by lyricsViewModel.romanizeHindi.collectAsStateWithLifecycle()
    val romanizeJapanese by lyricsViewModel.romanizeJapanese.collectAsStateWithLifecycle()
    val romanizeKorean by lyricsViewModel.romanizeKorean.collectAsStateWithLifecycle()
    val romanizeOtherLanguages by lyricsViewModel.romanizeOtherLanguages.collectAsStateWithLifecycle()
    val romanizationPreferences =
        remember(
            romanizeJapanese,
            romanizeKorean,
            romanizeChinese,
            romanizeHindi,
            romanizeOtherLanguages,
        ) {
            LyricsRomanizationPreferences(
                romanizeJapanese = romanizeJapanese,
                romanizeKorean = romanizeKorean,
                romanizeChinese = romanizeChinese,
                romanizeHindi = romanizeHindi,
                romanizeOther = romanizeOtherLanguages,
            )
        }

    val inactiveAlpha = 0.35f

    // ── Selection mode state ──
    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }

    // ── Parse lyrics into entries ──
    val isSynced = remember(lyrics) { lyrics != null && (isLineSyncedLrc(lyrics) || isTtml(lyrics)) }
    val isTtmlFormat = remember(lyrics) { lyrics != null && isTtml(lyrics) }

    val entriesWithWords: List<LyricsEntry> =
        remember(lyrics) {
            if (lyrics == null || lyrics == LYRICS_NOT_FOUND) return@remember emptyList()
            val parsed =
                when {
                    isTtml(lyrics) -> {
                        parseTtml(lyrics)
                    }

                    isLineSyncedLrc(lyrics) -> {
                        insertInstrumentalBreaks(parseLyrics(lyrics), durationMs.coerceAtLeast(0L))
                    }

                    else -> {
                        lyrics
                            .lines()
                            .filter { it.isNotBlank() }
                            .map { line -> LyricsEntry(time = -1L, text = line.trim()) }
                    }
                }
            if (parsed.isNotEmpty() && parsed.first().time >= 0) {
                listOf(HEAD_LYRICS_ENTRY) + parsed
            } else {
                parsed
            }
        }

    // ── Romanization ──
    LaunchedEffect(entriesWithWords, romanizationPreferences) {
        if (!romanizationPreferences.isEnabled) {
            entriesWithWords.forEach { entry ->
                if (entry.romanizedTextFlow.value != null) {
                    entry.romanizedTextFlow.value = null
                }
            }
            return@LaunchedEffect
        }

        entriesWithWords.forEach { entry ->
            val providerRomanized = providedRomanizedTextForEntry(entry, romanizationPreferences)
            if (providerRomanized != null) {
                if (entry.romanizedTextFlow.value != providerRomanized) {
                    entry.romanizedTextFlow.value = providerRomanized
                }
                return@forEach
            }

            if (!shouldRomanizeLyricsLine(entry.text, romanizationPreferences)) {
                if (entry.romanizedTextFlow.value != null) {
                    entry.romanizedTextFlow.value = null
                }
                return@forEach
            }

            launch {
                val romanized =
                    try {
                        romanizeLyricsLine(entry.text, romanizationPreferences)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                entry.romanizedTextFlow.value = romanized
            }
        }
    }

    // ── Playback position tracking ──
    val leadMs = if (isTtmlFormat) TTML_LEAD_MS else LRC_LEAD_MS
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var currentLineIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(entriesWithWords, isSynced, leadMs, lyricsSyncOffset) {
        if (!isSynced || entriesWithWords.isEmpty()) return@LaunchedEffect
        val pollIntervalMs = if (isTtmlFormat) 16L else 50L
        while (isActive) {
            val pos = sliderPositionProvider() ?: positionMsProvider()

            playbackPositionMs = (pos + lyricsSyncOffset.toLong()).coerceAtLeast(0L)
            currentPositionMs = (playbackPositionMs + leadMs + LYRIC_VISUAL_TUNING_OFFSET_MS).coerceAtLeast(0L)

            currentLineIndex = findCurrentLineIndex(entriesWithWords, currentPositionMs, 0L)
            delay(pollIntervalMs)
        }
    }

    // ── Scroll State ──
    val listState = rememberLazyListState()
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    // Detect manual scrolling
    val nestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                        isManualScrolling = true
                        lastManualScrollTime = System.currentTimeMillis()
                    }
                    return Offset.Zero
                }
            }
        }

    // Resume auto-scroll after timeout
    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    // Auto-scroll to active line
    LaunchedEffect(currentLineIndex, isManualScrolling, lyricsScroll) {
        if (!lyricsScroll || isManualScrolling || !isSynced) return@LaunchedEffect
        if (currentLineIndex < 0 || currentLineIndex >= entriesWithWords.size) return@LaunchedEffect

        val visibleInfo = listState.layoutInfo
        val viewportHeight = visibleInfo.viewportSize.height
        val targetOffset = (viewportHeight * 0.35f).toInt() // Center bias at 35% from top

        val distance = abs(currentLineIndex - (listState.firstVisibleItemIndex))
        if (distance > 15) {
            // Far jump — snap first, then settle
            listState.scrollToItem(
                (currentLineIndex - 2).coerceAtLeast(0),
                0,
            )
        }
        listState.animateScrollToItem(
            index = currentLineIndex,
            scrollOffset = -targetOffset,
        )
    }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.player_max_selection_limit, MAX_SELECTION_LIMIT),
                    Toast.LENGTH_SHORT,
                ).show()
            showMaxSelectionToast = false
        }
    }

    // ── Keep screen alive ──
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Render ──
    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 12.dp),
    ) {
        if (lyrics == null) {
            ShimmerHost {
                repeat(6) {
                    TextPlaceholder()
                }
            }
            return@BoxWithConstraints
        }

        if (entriesWithWords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.player_lyrics_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .smoothFadingEdge(vertical = 80.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = entriesWithWords,
                key = { index, entry -> "${index}_${entry.time}" },
                contentType = { _, entry ->
                    when {
                        entry == HEAD_LYRICS_ENTRY -> "head"
                        entry.isInstrumental -> "instrumental"
                        entry.words != null && isSynced -> "wordSynced"
                        else -> "lineSynced"
                    }
                },
            ) { index, item ->
                if (item == HEAD_LYRICS_ENTRY) {
                    Spacer(modifier = Modifier.height(120.dp))
                    return@itemsIndexed
                }

                // ── Instrumental break icon ──
                if (item.isInstrumental && isSynced) {
                    val startTimeMs = item.time
                    val endTimeMs = item.time + item.durationMs
                    val isActive = playbackPositionMs in startTimeMs until endTimeMs
                    val distanceFromActive = abs(index - currentLineIndex)
                    val instrAlpha =
                        when {
                            isActive -> {
                                1f
                            }

                            isManualScrolling -> {
                                when (distanceFromActive) {
                                    1 -> 0.72f
                                    2 -> 0.56f
                                    3 -> 0.40f
                                    else -> 0.28f
                                }
                            }

                            distanceFromActive == 1 -> {
                                0.52f
                            }

                            distanceFromActive == 2 -> {
                                0.30f
                            }

                            distanceFromActive == 3 -> {
                                0.18f
                            }

                            else -> {
                                inactiveAlpha
                            }
                        }
                    val animatedInstrAlpha by animateFloatAsState(
                        targetValue = instrAlpha,
                        animationSpec =
                            tween(
                                durationMillis = if (isActive) 330 else 500,
                                easing = FastOutSlowInEasing,
                            ),
                        label = "v2InstrumentalAlpha",
                    )
                    val animatedInstrScale by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.95f,
                        animationSpec =
                            tween(
                                durationMillis = 166,
                                easing = FastOutSlowInEasing,
                            ),
                        label = "v2InstrumentalScale",
                    )
                    val targetInstrBlur =
                        when {
                            isActive || isManualScrolling -> 0f
                            distanceFromActive == 1 -> 2f
                            distanceFromActive == 2 -> 5f
                            else -> 12f
                        }
                    val animatedInstrBlur by animateFloatAsState(
                        targetValue = targetInstrBlur,
                        animationSpec =
                            tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing,
                            ),
                        label = "v2InstrumentalBlur",
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top =
                                        if (index == 0 || (index == 1 && entriesWithWords[0] == HEAD_LYRICS_ENTRY)) {
                                            0.dp
                                        } else {
                                            (lyricsLineSpacing * 8).dp
                                        },
                                    bottom = (lyricsLineSpacing * 8).dp,
                                ).then(
                                    if (lyricsLineBlur) {
                                        Modifier.blur(
                                            radiusX = animatedInstrBlur.dp,
                                            radiusY = animatedInstrBlur.dp,
                                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).graphicsLayer {
                                    scaleX = animatedInstrScale
                                    scaleY = animatedInstrScale
                                    alpha = animatedInstrAlpha
                                }.then(
                                    if (lyricsClick && item.time > 0) {
                                        Modifier.clickable { onSeek(item.time) }
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        InstrumentalBreakItem(
                            durationMs = item.durationMs,
                            currentPositionMs = playbackPositionMs,
                            startTimeMs = startTimeMs,
                            textColor = textColor,
                            inactiveAlpha = inactiveAlpha,
                        )
                    }
                    return@itemsIndexed
                }

                // ── Agent-based positioning ──
                // v1 or null -> Start, v2 -> End, others -> Center
                val textAlign =
                    when (item.agent?.lowercase()) {
                        "v1", null -> TextAlign.Start
                        "v2" -> TextAlign.End
                        else -> TextAlign.Center
                    }
                val horizontalAlignment =
                    when (item.agent?.lowercase()) {
                        "v1", null -> Alignment.Start
                        "v2" -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }

                val isActive = isSynced && index == currentLineIndex
                val isPast = isSynced && index < currentLineIndex
                val isSelected = selectedIndices.contains(index)

                // Distance-based alpha for non-active lines
                val distanceFromActive = if (isSynced) abs(index - currentLineIndex) else 0
                val lineAlpha =
                    when {
                        !isSynced -> {
                            0.92f
                        }

                        isActive -> {
                            1f
                        }

                        isManualScrolling -> {
                            when (distanceFromActive) {
                                1 -> 0.72f
                                2 -> 0.56f
                                3 -> 0.40f
                                else -> 0.28f
                            }
                        }

                        distanceFromActive == 1 -> {
                            0.52f
                        }

                        distanceFromActive == 2 -> {
                            0.30f
                        }

                        distanceFromActive == 3 -> {
                            0.18f
                        }

                        else -> {
                            0.10f
                        }
                    }
                val targetBlur =
                    when {
                        !isSynced || isActive || (isSelectionModeActive && isSelected) || isManualScrolling -> 0f
                        distanceFromActive == 1 -> 2f
                        distanceFromActive == 2 -> 5f
                        else -> 12f
                    }
                val animatedBlur by animateFloatAsState(
                    targetValue = targetBlur,
                    animationSpec =
                        tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing,
                        ),
                    label = "v2LyricBlur",
                )
                val animatedLineScale by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0.95f,
                    animationSpec =
                        tween(
                            durationMillis = 166,
                            easing = FastOutSlowInEasing,
                        ),
                    label = "v2LineScale",
                )
                val animatedLineAlpha by animateFloatAsState(
                    targetValue = lineAlpha,
                    animationSpec =
                        tween(
                            durationMillis = if (isActive) 330 else 500,
                            easing = FastOutSlowInEasing,
                        ),
                    label = "v2LineAlpha",
                )
                val lineTransformOrigin =
                    remember(item.agent) {
                        when (item.agent?.lowercase()) {
                            "v2" -> TransformOrigin(1f, 0.5f)
                            "v1", null -> TransformOrigin(0f, 0.5f)
                            else -> TransformOrigin(0.5f, 0.5f)
                        }
                    }

                // Background vocal detection
                val isAllBackground = item.words?.all { it.isBackground || it.text.isBlank() } == true
                val baseLayoutDirection = LocalLayoutDirection.current
                val lineText =
                    remember(item.text, item.words) {
                        item.words
                            ?.joinToString(separator = "") { it.text }
                            ?.takeIf { it.isNotBlank() }
                            ?: item.text
                    }
                val lineIsRtl = remember(lineText) { isRtlText(lineText) }
                val lineLayoutDirection =
                    remember(lineIsRtl, baseLayoutDirection) {
                        if (lineIsRtl) LayoutDirection.Rtl else baseLayoutDirection
                    }

                CompositionLocalProvider(LocalLayoutDirection provides lineLayoutDirection) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color =
                                        if (isSelected && isSelectionModeActive) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        } else {
                                            Color.Transparent
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                ).padding(
                                    start = if (isAllBackground) 24.dp else 12.dp,
                                    end = 12.dp,
                                    top =
                                        if (index == 0 ||
                                            (index == 1 && entriesWithWords[0] == HEAD_LYRICS_ENTRY)
                                        ) {
                                            0.dp
                                        } else {
                                            (lyricsLineSpacing * 8).dp
                                        },
                                    bottom = (lyricsLineSpacing * 8).dp,
                                ).then(
                                    if (lyricsLineBlur) {
                                        Modifier.blur(
                                            radiusX = animatedBlur.dp,
                                            radiusY = animatedBlur.dp,
                                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).graphicsLayer {
                                    scaleX = animatedLineScale
                                    scaleY = animatedLineScale
                                    alpha = animatedLineAlpha
                                    transformOrigin = lineTransformOrigin
                                }.combinedClickable(
                                    onClick = {
                                        if (isSelectionModeActive) {
                                            if (isSelected) {
                                                selectedIndices.remove(index)
                                                if (selectedIndices.isEmpty()) {
                                                    isSelectionModeActive = false
                                                }
                                            } else {
                                                if (selectedIndices.size < MAX_SELECTION_LIMIT) {
                                                    selectedIndices.add(index)
                                                } else {
                                                    showMaxSelectionToast = true
                                                }
                                            }
                                        } else if (lyricsClick && isSynced && item.time > 0) {
                                            onSeek(item.time)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionModeActive) {
                                            isSelectionModeActive = true
                                            selectedIndices.add(index)
                                        } else if (!isSelected && selectedIndices.size < MAX_SELECTION_LIMIT) {
                                            selectedIndices.add(index)
                                        } else if (!isSelected) {
                                            showMaxSelectionToast = true
                                        }
                                    },
                                ),
                        horizontalAlignment = horizontalAlignment,
                    ) {
                        val romanizedText =
                            if (romanizationPreferences.isEnabled) {
                                val value by item.romanizedTextFlow.collectAsStateWithLifecycle()
                                value
                            } else {
                                null
                            }

                        if (romanizedText != null) {
                            val supplementaryBaseTextStyle = MaterialTheme.typography.bodyMedium
                            val supplementaryTextStyle =
                                remember(supplementaryBaseTextStyle, lyricsTextSize, isAllBackground) {
                                    supplementaryBaseTextStyle.copy(
                                        fontSize = (lyricsTextSize * 0.55f).sp,
                                        lineHeight = (lyricsTextSize * 0.75f).sp,
                                        fontWeight = FontWeight.Normal,
                                        fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                    )
                                }
                            Text(
                                text = romanizedText,
                                style = supplementaryTextStyle,
                                color = textColor.copy(alpha = if (isActive) 0.76f else 0.42f),
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = (lyricsTextSize * 0.18f).dp),
                            )
                        }

                        val words = item.words
                        if (words != null && isSynced) {
                            LyricsLineV2(
                                words = words,
                                isActive = isActive,
                                isPast = isPast,
                                currentPositionMs = currentPositionMs,
                                textColor = textColor,
                                inactiveAlpha = inactiveAlpha,
                                baseFontSize = lyricsTextSize,
                                isLineAllBackground = isAllBackground,
                                textAlign = textAlign,
                                isRtl = lineIsRtl,
                                bounceFactor = bounceFactor,
                                glowFactor = glowFactor,
                                fillTransitionWidth = fillTransitionWidth,
                            )
                        } else if (isSynced) {
                            LyricsLineLrcBounce(
                                text = item.text,
                                isActive = isActive,
                                textColor = textColor.copy(alpha = if (isActive) 1f else 0.52f),
                                fontSize = lyricsTextSize,
                                lineSpacing = lyricsLineSpacing,
                                isAllBackground = isAllBackground,
                                textAlign = textAlign,
                                bounceFactor = if (lrcBounceEnabled) bounceFactor else 0f,
                            )
                        } else {
                            Text(
                                text = item.text,
                                style =
                                    MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = if (isAllBackground) (lyricsTextSize * 0.82f).sp else lyricsTextSize.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                        lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                    ),
                                color = textColor.copy(alpha = if (isActive) 1f else 0.52f),
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // Bottom spacer for overscroll
            item {
                Spacer(modifier = Modifier.height(300.dp))
            }
        }

        // ── Resume auto-scroll button ──
        if (isManualScrolling && isSynced) {
            FilledTonalButton(
                onClick = {
                    isManualScrolling = false
                    scope.launch {
                        val viewportHeight = listState.layoutInfo.viewportSize.height
                        listState.animateScrollToItem(
                            index = currentLineIndex,
                            scrollOffset = -(viewportHeight * 0.35f).toInt(),
                        )
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                shapes = buttonPressShapes(),
            ) {
                Text(
                    text = stringResource(R.string.player_lyrics_resume),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (isSelectionModeActive) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    shape = CircleShape,
                                ).clickable {
                                    isSelectionModeActive = false
                                    selectedIndices.clear()
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.close),
                            contentDescription = stringResource(R.string.player_close),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .background(
                                    color =
                                        if (selectedIndices.isNotEmpty()) {
                                            Color.White.copy(alpha = 0.9f)
                                        } else {
                                            Color.White.copy(alpha = 0.5f)
                                        },
                                    shape = RoundedCornerShape(24.dp),
                                ).clickable(enabled = selectedIndices.isNotEmpty()) {
                                    val selectedLyricsText =
                                        selectedIndices
                                            .sorted()
                                            .mapNotNull { entriesWithWords.getOrNull(it)?.text }
                                            .joinToString("\n")

                                    if (selectedLyricsText.isNotBlank()) {
                                        context.shareLyricsAsText(selectedLyricsText, track)
                                    }
                                    isSelectionModeActive = false
                                    selectedIndices.clear()
                                }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.player_share),
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/** Offers the selected lines, quoted and signed with the track, to another app - ArchiveTune's `shareLyricsAsText`. */
private fun Context.shareLyricsAsText(
    lyricsText: String,
    track: Track,
) {
    val shareBody =
        buildString {
            append("\"")
            append(lyricsText)
            append("\"\n\n")
            append(track.title)
            append(" - ")
            append(track.artist)
        }

    val shareIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
    startActivity(Intent.createChooser(shareIntent, getString(R.string.player_share_lyrics)))
}

// ──────────────────────────────────────────────────────────────────────
// Line-level composable: renders words with fluid fill animation
// ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineV2(
    words: List<WordTimestamp>,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    baseFontSize: Float,
    isLineAllBackground: Boolean,
    textAlign: TextAlign,
    isRtl: Boolean,
    bounceFactor: Float,
    glowFactor: Float,
    fillTransitionWidth: Float,
) {
    val arrangement =
        when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.End -> Arrangement.End
            else -> Arrangement.Start
        }

    // Split words into main and background
    val mainWords = words.filter { !it.isBackground }
    val bgWords = words.filter { it.isBackground }

    // 1. Render main words First (if any)
    if (mainWords.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = arrangement,
        ) {
            mainWords.forEach { word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = if (isLineAllBackground) (baseFontSize * 0.82f).sp else baseFontSize.sp,
                            ),
                        color = Color.Transparent,
                    )
                    return@forEach
                }
                if (word.text == "\n") {
                    Spacer(modifier = Modifier.fillMaxWidth())
                    return@forEach
                }

                AnimatedWordV2(
                    word = word,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = currentPositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = if (isLineAllBackground) baseFontSize * 0.82f else baseFontSize,
                    isBackground = isLineAllBackground,
                    isRtl = isRtl,
                    bounceFactor = bounceFactor,
                    glowFactor = glowFactor,
                    fillTransitionWidth = fillTransitionWidth,
                )
            }
        }
    }

    // 2. Render background words explicitly on a NEW line, noticeably smaller
    if (bgWords.isNotEmpty()) {
        if (mainWords.isNotEmpty()) Spacer(modifier = Modifier.height(4.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth().alpha(0.85f), // Slightly dimmer overall
            horizontalArrangement = arrangement,
        ) {
            bgWords.forEach { word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = (baseFontSize * 0.65f).sp,
                            ),
                        color = Color.Transparent,
                    )
                    return@forEach
                }

                AnimatedWordV2(
                    word = word,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = currentPositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = baseFontSize * 0.65f, // ~65% size of main text
                    isBackground = true, // Force dimmer styling inside AnimatedWordV2
                    isRtl = isRtl,
                    bounceFactor = bounceFactor,
                    glowFactor = glowFactor,
                    fillTransitionWidth = fillTransitionWidth,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Word-level composable: liquid fill sweep + glow + bounce
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedWordV2(
    word: WordTimestamp,
    isLineActive: Boolean,
    isLinePast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    fontSize: Float,
    isBackground: Boolean,
    isRtl: Boolean,
    bounceFactor: Float,
    glowFactor: Float,
    fillTransitionWidth: Float,
) {
    val wordStartMs = (word.startTime * 1000).toLong()
    val wordEndMs = (word.endTime * 1000).toLong()
    val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

    val isWordComplete = currentPositionMs >= wordEndMs
    val isWordActive = currentPositionMs in wordStartMs until wordEndMs

    // Perfect linear progress [0..1] that matches individual word timings
    val progress =
        when {
            isWordComplete -> 1f
            currentPositionMs <= wordStartMs -> 0f
            else -> ((currentPositionMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
        }

    // ── Bounce and Float animation ──
    // Subtle scale up peaking halfway through the word. Exact timing sync!
    val sinProgress = sin(progress * PI).toFloat()
    val wordScale = 1f + (0.015f * bounceFactor * sinProgress)

    // Float is only applied when the word is actively sung, making it pop from the line.
    // We use animateFloatAsState so that when it finishes (and drops to 0f),
    // it smoothly decays back into place rather than a harsh mathematical snap.
    val targetFloat = if (isWordActive) -4f * bounceFactor * sinProgress else 0f
    val floatOffset by animateFloatAsState(
        targetValue = targetFloat,
        animationSpec =
            tween(
                durationMillis = if (isWordActive) 50 else 350,
                easing = FastOutSlowInEasing,
            ),
        label = "v2FloatOffset",
    )

    // ── Glow intensity ──
    // "lines and words that are done animating shouldnt continue to glow"
    // Make glow build up faster: reach max intensity at 50% progress
    val glowProgress = (progress * 2f).coerceAtMost(1f)
    val glowAlpha = if (isWordActive) glowProgress * 0.45f * glowFactor else 0f
    val glowRadius = if (isWordActive) glowProgress * 12f * glowFactor else 0f

    val actualFontSize = if (isBackground) fontSize * 0.85f else fontSize
    val fontWeight = if (isLineActive || isLinePast) FontWeight.ExtraBold else FontWeight.SemiBold
    val glowPadding = 10.dp

    // ── Two-layer rendering: dim base + liquid fill overlay ──
    Box(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val glowPaddingPx = glowPadding.roundToPx()
                    val looseConstraints =
                        constraints.copy(
                            minWidth = 0,
                            maxWidth = constraints.maxWidth,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity,
                        )
                    val placeable = measurable.measure(looseConstraints)

                    val coreWidth = (placeable.width - glowPaddingPx * 2).coerceAtLeast(0)
                    val coreHeight = (placeable.height - glowPaddingPx * 2).coerceAtLeast(0)

                    layout(coreWidth, coreHeight) {
                        placeable.place(-glowPaddingPx, -glowPaddingPx)
                    }
                }.graphicsLayer {
                    clip = false
                    translationY = floatOffset * density
                    scaleX = wordScale
                    scaleY = wordScale
                },
    ) {
        // Layer 1: Base text (always dimmed)
        Text(
            text = word.text,
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontSize = actualFontSize.sp,
                    fontWeight = fontWeight,
                    fontStyle = FontStyle.Normal,
                    lineHeight = (actualFontSize * 1.35f).sp,
                ),
            color = textColor.copy(alpha = if (isBackground) inactiveAlpha * 0.7f else inactiveAlpha),
            modifier = Modifier.padding(glowPadding),
        )

        // Layer 2: Filled overlay with liquid sweep mask + glow
        if (isWordComplete || isWordActive || isLinePast) {
            Text(
                text = word.text,
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = actualFontSize.sp,
                        fontWeight = fontWeight,
                        fontStyle = FontStyle.Normal,
                        lineHeight = (actualFontSize * 1.35f).sp,
                        shadow =
                            if (glowAlpha > 0f) {
                                Shadow(
                                    color = textColor.copy(alpha = glowAlpha),
                                    offset = Offset.Zero,
                                    blurRadius = glowRadius.coerceAtLeast(1f),
                                )
                            } else {
                                null
                            },
                    ),
                color =
                    textColor.copy(
                        alpha = if (isBackground) 0.75f else 1f,
                    ),
                modifier =
                    if (isWordActive) {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val edgeWidth = fillTransitionWidth.dp.toPx()
                                val center =
                                    if (isRtl) {
                                        size.width - ((size.width + edgeWidth * 2) * progress - edgeWidth)
                                    } else {
                                        (size.width + edgeWidth * 2) * progress - edgeWidth
                                    }
                                drawRect(
                                    brush =
                                        Brush.horizontalGradient(
                                            colors =
                                                if (isRtl) {
                                                    listOf(Color.Transparent, Color.Black)
                                                } else {
                                                    listOf(Color.Black, Color.Transparent)
                                                },
                                            startX = center - edgeWidth,
                                            endX = center + edgeWidth,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }.padding(glowPadding)
                    } else {
                        Modifier.padding(glowPadding)
                    },
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// LRC bounce: word-by-word spring bounce for line-synced lyrics
// ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineLrcBounce(
    text: String,
    isActive: Boolean,
    textColor: Color,
    fontSize: Float,
    lineSpacing: Float,
    isAllBackground: Boolean,
    textAlign: TextAlign,
    bounceFactor: Float,
) {
    val words = remember(text) { text.toLyricsWrappingUnits() }
    val effectiveFontSize = if (isAllBackground) fontSize * 0.82f else fontSize
    val fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold
    val fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal
    val scaleAnimatables = remember(words.size) { List(words.size) { Animatable(1f) } }
    val floatAnimatables = remember(words.size) { List(words.size) { Animatable(0f) } }

    LaunchedEffect(isActive) {
        if (!isActive || bounceFactor == 0f) return@LaunchedEffect
        words.indices.forEach { i ->
            launch {
                delay(i * 40L)
                try {
                    scaleAnimatables[i].animateTo(
                        targetValue = 1f + 0.045f * bounceFactor,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                    )
                    scaleAnimatables[i].animateTo(
                        targetValue = 1f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    )
                } finally {
                    withContext(NonCancellable) { scaleAnimatables[i].snapTo(1f) }
                }
            }
            launch {
                delay(i * 40L)
                try {
                    floatAnimatables[i].animateTo(
                        targetValue = -5f * bounceFactor,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                    )
                    floatAnimatables[i].animateTo(
                        targetValue = 0f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    )
                } finally {
                    withContext(NonCancellable) { floatAnimatables[i].snapTo(0f) }
                }
            }
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            when (textAlign) {
                TextAlign.Center -> Arrangement.Center
                TextAlign.End -> Arrangement.End
                else -> Arrangement.Start
            },
    ) {
        words.forEachIndexed { i, word ->
            LrcBouncingWord(
                text = word,
                scaleAnim = scaleAnimatables[i],
                floatAnim = floatAnimatables[i],
                color = textColor,
                fontSize = effectiveFontSize,
                lineSpacing = lineSpacing,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
            )
        }
    }
}

@Composable
private fun LrcBouncingWord(
    text: String,
    scaleAnim: Animatable<Float, AnimationVector1D>,
    floatAnim: Animatable<Float, AnimationVector1D>,
    color: Color,
    fontSize: Float,
    lineSpacing: Float,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.headlineMedium.copy(
                fontSize = fontSize.sp,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                lineHeight = (fontSize * lineSpacing).sp,
            ),
        color = color,
        modifier =
            Modifier.graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationY = floatAnim.value
            },
    )
}

// ──────────────────────────────────────────────────────────────────────
// Instrumental break icon: music-note filled bottom-to-top over the gap
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun InstrumentalBreakItem(
    durationMs: Long,
    currentPositionMs: Long,
    startTimeMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
) {
    val musicNotePath =
        remember {
            PathParser()
                .parsePathString(
                    "M10 21q-1.65 0-2.825-1.175T6 17t1.175-2.825T10 13q.575 0 1.063.138t.937.412V4" +
                        "q0-.425.288-.712T13 3h4q.425 0 .713.288T18 4v2q0 .425-.288.713T17 7h-3v10" +
                        "q0 1.65-1.175 2.825T10 21",
                ).toPath()
        }

    val targetFillFraction =
        when {
            durationMs <= 0L -> {
                0f
            }

            currentPositionMs <= startTimeMs -> {
                0f
            }

            currentPositionMs >= startTimeMs + durationMs -> {
                1f
            }

            else -> {
                ((currentPositionMs - startTimeMs).toDouble() / durationMs.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
        }
    val fillFraction by animateFloatAsState(
        targetValue = targetFillFraction,
        animationSpec =
            spring(
                stiffness = Spring.StiffnessHigh,
                dampingRatio = Spring.DampingRatioNoBouncy,
            ),
        label = "instrumentalFill",
    )

    Canvas(modifier = Modifier.size(48.dp)) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        val pivot = Offset.Zero

        withTransform(
            transformBlock = { scale(scaleX, scaleY, pivot) },
        ) {
            drawPath(path = musicNotePath, color = textColor.copy(alpha = inactiveAlpha))
        }

        if (fillFraction > 0f) {
            val clipTop = size.height * (1f - fillFraction)
            clipRect(
                left = 0f,
                top = clipTop,
                right = size.width,
                bottom = size.height,
            ) {
                withTransform(
                    transformBlock = { scale(scaleX, scaleY, pivot) },
                ) {
                    drawPath(path = musicNotePath, color = textColor)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Loading placeholder, fading edges and word wrapping
// ──────────────────────────────────────────────────────────────────────

/** Placeholder lines while the lyrics are read - ArchiveTune's `ShimmerHost`, on Sona's shimmer. */
@Composable
private fun ShimmerHost(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .shimmer()
                .graphicsLayer(alpha = 0.99f)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
                        blendMode = BlendMode.DstIn,
                    )
                },
    ) {
        content()
    }
}

@Composable
private fun TextPlaceholder() {
    Box(
        modifier =
            Modifier
                .padding(vertical = 4.dp)
                .height(16.dp)
                .fillMaxWidth(remember { 0.25f + Random.nextFloat() * 0.5f })
                .clip(RoundedCornerShape(0.dp))
                .background(MaterialTheme.colorScheme.onSurface),
    )
}

/** Fades the first and last [vertical] of the content out - ArchiveTune's `smoothFadingEdge`. */
private fun Modifier.smoothFadingEdge(vertical: Dp) =
    graphicsLayer(alpha = 0.99f)
        .drawWithContent {
            drawContent()
            val topPx = vertical.toPx()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0.0f to Color.Transparent,
                                0.3f to Color.Black.copy(alpha = 0.15f),
                                0.5f to Color.Black.copy(alpha = 0.4f),
                                0.7f to Color.Black.copy(alpha = 0.7f),
                                0.85f to Color.Black.copy(alpha = 0.9f),
                                1.0f to Color.Black,
                            ),
                        startY = 0f,
                        endY = topPx,
                    ),
                blendMode = BlendMode.DstIn,
            )
            val bottomPx = vertical.toPx()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0.0f to Color.Black,
                                0.15f to Color.Black.copy(alpha = 0.9f),
                                0.3f to Color.Black.copy(alpha = 0.7f),
                                0.5f to Color.Black.copy(alpha = 0.4f),
                                0.7f to Color.Black.copy(alpha = 0.15f),
                                1.0f to Color.Transparent,
                            ),
                        startY = size.height - bottomPx,
                        endY = size.height,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }

/** Splits a line into the units it may wrap between: whole words, and single CJK characters. */
private fun String.toLyricsWrappingUnits(): List<String> {
    if (isEmpty()) return emptyList()

    val units = mutableListOf<String>()
    val currentWord = StringBuilder()
    val characterIterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    characterIterator.setText(this)

    fun flushCurrentWord() {
        if (currentWord.isNotEmpty()) {
            units += currentWord.toString()
            currentWord.clear()
        }
    }

    var start = characterIterator.first()
    var end = characterIterator.next()
    while (end != BreakIterator.DONE) {
        val grapheme = substring(start, end)
        val codePoint = grapheme.codePointAt(0)
        when {
            grapheme.all(Char::isWhitespace) -> {
                currentWord.append(grapheme)
                flushCurrentWord()
            }

            codePoint.isCjkCodePoint() -> {
                flushCurrentWord()
                units += grapheme
            }

            else -> {
                currentWord.append(grapheme)
            }
        }
        start = end
        end = characterIterator.next()
    }
    flushCurrentWord()

    return units
}

private fun Int.isCjkCodePoint(): Boolean =
    when (Character.UnicodeScript.of(this)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        -> true

        else -> false
    }
