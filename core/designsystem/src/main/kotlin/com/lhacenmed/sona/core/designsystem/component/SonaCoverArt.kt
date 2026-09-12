package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** The look every cover shares, and the one place a future cover-shape setting has to reach. */
object CoverArtDefaults {

    /** The size a cover takes in a list row. */
    val ListSize = 56.dp

    /**
     * The corners every cover is cut with.
     *
     * A single value rather than one per call site because the reference app makes roundness a
     * setting: when that arrives it replaces this, and every cover in the app follows without any of
     * them having to be found first.
     */
    val Shape: Shape = RoundedCornerShape(12.dp)

    /** How much of the cover the playing indicator occupies when it stands in for one. */
    internal val IndicatorInset = 16.dp
}

/**
 * A piece of cover art, or - while its track is the one being played - the playing indicator in its
 * place.
 *
 * The indicator replaces the artwork rather than sitting on top of it, which is what lets a row say
 * "this one is playing" without tinting the row itself: the cover is the only thing that changes,
 * so the list keeps its rhythm and a selected row still reads as selected underneath.
 *
 * Takes a [coverArtUri] rather than any domain type, so albums, artists and genres can use the same
 * cover as tracks do.
 */
@Composable
fun SonaCoverArt(
    coverArtUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = CoverArtDefaults.Shape,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(CoverArtDefaults.ListSize)
            .clip(shape)
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
    ) {
        if (isCurrent) {
            SonaPlayingIndicator(
                isPlaying = isPlaying,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CoverArtDefaults.IndicatorInset),
            )
        } else {
            AsyncImage(
                model = coverArtUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
