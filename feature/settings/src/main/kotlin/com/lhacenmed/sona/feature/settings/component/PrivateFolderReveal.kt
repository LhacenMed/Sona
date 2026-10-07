package com.lhacenmed.sona.feature.settings.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.motion.RubberBandOverscroll
import com.lhacenmed.sona.core.designsystem.motion.StartHold
import com.lhacenmed.sona.core.designsystem.motion.performSwipeArmHaptic
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop

/** How far past the list's end a pull must go before letting go opens the Private Folder. */
private val RevealThreshold = 72.dp

/**
 * The hidden "Private Folder" row pulled up from under the Settings list - see `SettingsList`'s
 * `onPrivateFolderRevealed`. Stretching the list past its own end - the same rubber band every list in
 * the app already stretches on - fades this in from below; letting go past [RevealThreshold] opens it,
 * letting go short of it just settles the band back with nothing shown.
 *
 * Deliberately not a normal, always-visible settings row: the Private Folder is reached only this way.
 */
@Composable
internal fun BoxScope.PrivateFolderOverscrollReveal(overscroll: RubberBandOverscroll, onRevealed: () -> Unit) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { RevealThreshold.toPx() }
    val reveal = remember(overscroll, thresholdPx) { PrivateFolderReveal(overscroll, onRevealed, thresholdPx) }

    DisposableEffect(overscroll, reveal) {
        overscroll.endHold = reveal
        onDispose { if (overscroll.endHold === reveal) overscroll.endHold = null }
    }

    val view = LocalView.current
    // Felt only as the finger crosses the point, either way - not as letting go there opens it.
    LaunchedEffect(reveal) {
        snapshotFlow { reveal.isArmed }.drop(1).collect { isArmed ->
            if (overscroll.isDragged) view.performSwipeArmHaptic(isArmed)
        }
    }

    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            // Hangs just below the list's bottom edge at rest, coming up into view as the band stretches -
            // the mirror of how SonaPullToRefreshBox's indicator hangs above the list's top.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(0, (placeable.height + overscroll.stretch).roundToInt())
                }
            }
            .alpha((-overscroll.stretch / thresholdPx).coerceIn(0f, 1f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null)
        Text("Private Folder", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Armed once a pull holds [thresholdPx] or more past the list's end; opens the Private Folder on release armed. */
private class PrivateFolderReveal(
    private val overscroll: RubberBandOverscroll,
    private val onReveal: () -> Unit,
    private val thresholdPx: Float,
) : StartHold {

    val isArmed: Boolean get() = overscroll.isDragged && -overscroll.stretch >= thresholdPx

    override val restingStretch: Float get() = 0f

    override fun onRelease(stretch: Float) {
        if (-stretch < thresholdPx) return
        onReveal()
    }
}
