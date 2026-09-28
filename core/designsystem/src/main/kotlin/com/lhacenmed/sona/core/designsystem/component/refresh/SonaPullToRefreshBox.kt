package com.lhacenmed.sona.core.designsystem.component.refresh

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.motion.RubberBandOverscroll
import com.lhacenmed.sona.core.designsystem.motion.StartHold
import com.lhacenmed.sona.core.designsystem.motion.performSwipeArmHaptic
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The gap above and below the indicator while the list is held open under it. */
private val IndicatorGap = 12.dp

/**
 * [content] - a list rubber-banded by [overscroll] - refreshed by pulling it down past its top: ArchiveTune's
 * expressive pull to refresh, on the app's own rubber band rather than Material's.
 *
 * The pull stretches the list, as any pull past its top does, and the indicator comes down with it in
 * the gap it leaves - the loading indicator's shape morphing as it comes, as Material's does, and turning
 * once the pull is far enough. Crossing that point is felt both ways, as a row's swipe crossing its arm
 * is: letting go now refreshes, or no longer will. Let go past it, the list is held open under the
 * indicator for as long as [onRefresh] runs, then settles back.
 *
 * While [onRefresh] is null nothing refreshes - the list rubber-bands as ever - and the layout is the same
 * either way, so turning refreshing on or off never rebuilds the list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SonaPullToRefreshBox(
    overscroll: RubberBandOverscroll,
    onRefresh: (suspend () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { PullToRefreshDefaults.PositionalThreshold.toPx() }
    val indicatorHeightPx = with(density) { LoadingIndicatorDefaults.ContainerHeight.toPx() }
    val gapPx = with(density) { IndicatorGap.toPx() }
    val scope = rememberCoroutineScope()
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val refresh = remember(overscroll, scope, thresholdPx, indicatorHeightPx, gapPx) {
        PullToRefresh(
            overscroll = overscroll,
            scope = scope,
            onRefresh = { latestOnRefresh },
            thresholdPx = thresholdPx,
            openStretchPx = indicatorHeightPx + gapPx * 2,
        )
    }

    val isEnabled = onRefresh != null
    DisposableEffect(overscroll, refresh, isEnabled) {
        if (isEnabled) overscroll.startHold = refresh
        onDispose { if (overscroll.startHold === refresh) overscroll.startHold = null }
    }

    val view = LocalView.current
    // Felt only as the finger crosses the point, either way - not as letting go there disarms it.
    LaunchedEffect(refresh) {
        snapshotFlow { refresh.isArmed }.drop(1).collect { isArmed ->
            if (overscroll.isDragged) view.performSwipeArmHaptic(isArmed)
        }
    }

    Box(modifier = modifier.clipToBounds()) {
        content()
        if (isEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Hangs just above the list's top as it is stretched, so it comes down with it -
                    // hidden above the edge at rest, and centred in the gap while the list is held open.
                    .layout { measurable, constraints ->
                        val indicator = measurable.measure(constraints)
                        layout(indicator.width, indicator.height) {
                            val top = overscroll.stretch - gapPx - indicator.height
                            indicator.place(0, top.roundToInt())
                        }
                    },
            ) {
                RefreshIndicator(
                    isRefreshing = refresh.isRefreshing,
                    pullFraction = { overscroll.stretch / thresholdPx },
                )
            }
        }
    }
}

/**
 * Material's expressive pull-to-refresh indicator - `PullToRefreshDefaults.LoadingIndicator`, in
 * ArchiveTune's colours: shaped by [pullFraction] as the list is pulled, turning past 1, and running on
 * its own while refreshing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshIndicator(isRefreshing: Boolean, pullFraction: () -> Float) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer
    val size = Modifier.size(LoadingIndicatorDefaults.ContainerWidth, LoadingIndicatorDefaults.ContainerHeight)
    Crossfade(targetState = isRefreshing, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) { refreshing ->
        if (refreshing) {
            ContainedLoadingIndicator(modifier = size, containerColor = containerColor, indicatorColor = indicatorColor)
        } else {
            ContainedLoadingIndicator(
                progress = pullFraction,
                // Past the point of refreshing it keeps turning with the pull, from where the morph ended.
                modifier = size.drawWithContent {
                    val fraction = pullFraction()
                    if (fraction > 1f) rotate(-(fraction - 1f) * 180f) { this@drawWithContent.drawContent() } else drawContent()
                },
                containerColor = containerColor,
                indicatorColor = indicatorColor,
            )
        }
    }
}

/**
 * The refresh a pull asks for: armed while a finger holds the list [thresholdPx] or more past its top,
 * run as it is let go armed, and holding the list [openStretchPx] open while it runs.
 */
private class PullToRefresh(
    private val overscroll: RubberBandOverscroll,
    private val scope: CoroutineScope,
    private val onRefresh: () -> (suspend () -> Unit)?,
    private val thresholdPx: Float,
    private val openStretchPx: Float,
) : StartHold {

    var isRefreshing by mutableStateOf(false)
        private set

    /** Whether letting go now refreshes. Never while a refresh already runs. */
    val isArmed: Boolean
        get() = !isRefreshing && overscroll.isDragged && overscroll.stretch >= thresholdPx

    override val restingStretch: Float
        get() = if (isRefreshing) openStretchPx else 0f

    override fun onRelease(stretch: Float) {
        if (isRefreshing || stretch < thresholdPx) return
        val refresh = onRefresh() ?: return
        isRefreshing = true
        scope.launch {
            try {
                refresh()
            } finally {
                isRefreshing = false
            }
            overscroll.settleToRest()
        }
    }
}
