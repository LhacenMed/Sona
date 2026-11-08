package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaMarquee
import com.lhacenmed.sona.core.designsystem.component.copyToClipboard
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Track
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What tapping and long-pressing the title and artist of any player style does. Ported from ArchiveTune.
 *
 * Opening the album or artist collapses the player first, so the screen it opens is what shows. [onArtistClick]
 * opens the artist given: one of the [creditedArtists] the track names, each tapped on its own - see
 * [ClickableArtist].
 */
@Immutable
internal class PlayerTitleActions(
    val onTitleClick: () -> Unit,
    val onArtistClick: (artistId: Long) -> Unit,
    val creditedArtists: List<Artist>,
    val onCopyTitle: () -> Unit,
    val onCopyArtists: () -> Unit,
)

@Composable
internal fun rememberPlayerTitleActions(
    track: Track,
    creditedArtists: List<Artist>,
    state: BottomSheetState,
    onGoToAlbum: ((Long) -> Unit)?,
    onGoToArtist: ((Long) -> Unit)?,
): PlayerTitleActions {
    val context = LocalContext.current
    val latestOnGoToAlbum by rememberUpdatedState(onGoToAlbum)
    val latestOnGoToArtist by rememberUpdatedState(onGoToArtist)

    return remember(track, creditedArtists, state, context) {
        PlayerTitleActions(
            // A track with no album or artist to go to - a Private Folder item's - goes nowhere.
            onTitleClick = {
                latestOnGoToAlbum?.let { goToAlbum ->
                    state.collapseSoft()
                    goToAlbum(track.albumId)
                }
            },
            onArtistClick = { artistId ->
                latestOnGoToArtist?.let { goToArtist ->
                    state.collapseSoft()
                    goToArtist(artistId)
                }
            },
            creditedArtists = creditedArtists,
            onCopyTitle = { context.copyToClipboard(track.title, context.getString(R.string.player_copied_title)) },
            onCopyArtists = { context.copyToClipboard(track.artist, context.getString(R.string.player_copied_artist)) },
        )
    }
}

/** Kept after the names within the line: names that would end closer than this to its edge scroll instead. */
private val ArtistLineEndGap = 12.dp

/** Where one of a credit's artists is named in it, and who. */
private class CreditedName(val range: IntRange, val artistId: Long)

/**
 * The artist line under a player's title, tapped to open the artist - ArchiveTune's `ClickableArtists`.
 *
 * A credit naming several [creditedArtists] opens whichever of them is tapped; anywhere else on the line, and
 * on a credit naming one, [onClick] opens the artist it names first. A long press anywhere on the line - on a
 * name too - is [onLongClick]. Too long for the line, it scrolls - each name tapped where it is as it goes, see
 * [SonaMarquee].
 *
 * Clicked as the title is, so a long press answers with the same haptic. Each name reads as the rest of the line does.
 */
@Composable
internal fun ClickableArtist(
    artist: String,
    creditedArtists: List<Artist>,
    onClick: () -> Unit,
    onCreditedArtistClick: (artistId: Long) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val latestOnCreditedArtistClick by rememberUpdatedState(onCreditedArtistClick)
    val creditedNames = remember(artist, creditedArtists) { creditedNamesIn(artist, creditedArtists) }
    SonaMarquee(
        modifier =
            modifier.combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = artist,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
            modifier =
                Modifier
                    .padding(end = ArtistLineEndGap)
                    .creditedNameTaps(creditedNames, { layout }) { latestOnCreditedArtistClick(it) },
        )
    }
}

/** Each of [credited] where [artist] names it, in the order it names them - none for a credit naming fewer than two. */
private fun creditedNamesIn(artist: String, credited: List<Artist>): List<CreditedName> {
    if (credited.size < 2) return emptyList()
    var searchFrom = 0
    return credited.mapNotNull { credit ->
        val start = artist.indexOf(credit.name, searchFrom, ignoreCase = true)
        if (start < 0) return@mapNotNull null
        searchFrom = start + credit.name.length
        CreditedName(start until searchFrom, credit.id)
    }
}

/**
 * Opens the artist a quick tap on one of [names] lands on, leaving a press held longer - and a tap anywhere else - to the
 * line around it: the press is only watched, never taken, until a tap on a name lifts within a long press's time, so
 * the line's long press still copies it.
 */
private fun Modifier.creditedNameTaps(
    names: List<CreditedName>,
    layout: () -> TextLayoutResult?,
    onNameClick: (artistId: Long) -> Unit,
): Modifier =
    if (names.isEmpty()) {
        this
    } else {
        pointerInput(names) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val offset = layout()?.getOffsetForPosition(down.position) ?: return@awaitEachGesture
                val name = names.firstOrNull { offset in it.range } ?: return@awaitEachGesture
                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                    ?: return@awaitEachGesture
                up.consume()
                onNameClick(name.artistId)
            }
        }
    }
