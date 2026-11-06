package com.lhacenmed.sona.feature.library.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCover
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.mediaCountLabel
import kotlin.math.roundToInt

/**
 * A selection of [tracks] picked up to be moved, held under the finger at [pointerInWindow]: their covers stacked into
 * one, as a playlist's are, beside how many they are - see
 * [SelectionMove][com.lhacenmed.sona.core.designsystem.component.SelectionMove].
 *
 * Laid over the whole of what it is given to fill. The finger is followed in the layout alone, so a move redraws this
 * and nothing else.
 */
@Composable
internal fun MovingTracks(
    tracks: List<Track>,
    pointerInWindow: () -> Offset,
    modifier: Modifier = Modifier,
) {
    var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val coverArtUris = remember(tracks) { rankedCoverArtUris(tracks.map { it.coverArtUri }) }
    Box(modifier = modifier.fillMaxSize().onPlaced { origin = it }) {
        Surface(
            shape = roundedShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(placeable.width, placeable.height) {
                    // Centred on the finger.
                    val finger = origin?.takeIf { it.isAttached }?.windowToLocal(pointerInWindow()) ?: return@layout
                    placeable.place(
                        x = (finger.x - placeable.width / 2f).roundToInt(),
                        y = (finger.y - placeable.height / 2f).roundToInt(),
                    )
                }
            },
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SonaPlaylistCover(
                    coverArtUris = coverArtUris,
                    seed = tracks.first().id.hashCode(),
                    size = CoverArtDefaults.CollectionListSize,
                )
                Text(text = mediaCountLabel(tracks), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
