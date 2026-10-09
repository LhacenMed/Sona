package com.lhacenmed.sona.core.designsystem.component.overscroll

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import com.lhacenmed.sona.core.designsystem.motion.RubberBandOverscroll

/**
 * [content] - a list rubber-banded by [overscroll] - refreshed by pulling it down past its top: ArchiveTune's
 * expressive pull to refresh, as an [OverscrollTrigger] on the app's own rubber band rather than Material's.
 *
 * The indicator comes down with the list in the gap it leaves - the loading indicator's shape morphing as
 * it comes, as Material's does, and turning once the pull is far enough - and the list is held open under
 * it for as long as [onRefresh] runs.
 *
 * While [onRefresh] is null nothing refreshes - the list rubber-bands as ever - and the layout is the same
 * either way, so turning refreshing on or off never rebuilds the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonaPullToRefreshBox(
    overscroll: RubberBandOverscroll,
    onRefresh: (suspend () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.clipToBounds()) {
        content()
        OverscrollTrigger(
            overscroll = overscroll,
            edge = OverscrollEdge.Start,
            threshold = PullToRefreshDefaults.PositionalThreshold,
            onTrigger = onRefresh,
        ) { refresh ->
            RefreshIndicator(isRefreshing = refresh.isRunning, pullFraction = { refresh.pullFraction })
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
