package com.lhacenmed.sona.feature.tageditor.autotag

import androidx.compose.runtime.Immutable
import com.lhacenmed.sona.core.model.UnknownNames
import com.lhacenmed.sona.feature.tageditor.MatchLookup
import com.lhacenmed.sona.feature.tageditor.TrackTagDraft
import com.lhacenmed.sona.feature.tageditor.lookup.TrackMatcher
import com.lhacenmed.sona.feature.tageditor.lookup.TrackQueries
import com.lhacenmed.sona.feature.tageditor.lyrics.LyricsText
import com.lhacenmed.sona.feature.tageditor.tags.TagField

/** The sets of lyrics a run keeps of all it finds for a track - enough to choose among, without a library's worth of every source's answer. */
private const val KeptLyrics = 3

/** What a run fills each track's draft with - tags, a cover, lyrics - and whether it leaves alone what the file already holds. */
@Immutable
data class AutoTagChoices(
    val findsTags: Boolean = true,
    val findsCover: Boolean = true,
    val findsLyrics: Boolean = true,
    /** Only what the file leaves blank is filled: a tag it has none of, a cover or lyrics where it has none. */
    val fillsBlanksOnly: Boolean = false,
) {
    val findsAnything: Boolean get() = findsTags || findsCover || findsLyrics
}

/** What a run made of one track - the group its review lists it under, in this order. */
enum class AutoTagOutcome(val title: String) {
    /** Matched convincingly: picked to be saved from the start. */
    READY("Ready to save"),

    /** Matched, but not convincingly enough to save unasked. */
    UNSURE("Needs a look"),
    NOT_FOUND("No match"),

    /** No catalogue could be reached - the one outcome worth trying again. */
    UNREACHABLE("Not looked up"),

    /** Matched, and the file already holds all of it. */
    UP_TO_DATE("Already up to date"),
}

/**
 * Fills this draft the way a run does, with no one asked: the best match every catalogue has, taken whole - or
 * only into the fields [choices] leaves to it - with its cover and the best lyrics that can be trusted unasked. It
 * tells which [AutoTagOutcome] the track is.
 *
 * A match's cover replaces the file's own - where [hasOwnCover] says it has one - only where the match changed its tags - the file was wrong, and its cover
 * likely with it - or where the cover alone was asked for. Lyrics replace the file's own only where they follow the
 * song more closely; and they are looked for under the names a convincing match gives the song, since a file's own
 * are often what was wrong.
 */
internal suspend fun TrackTagDraft.autoFill(choices: AutoTagChoices, hasOwnCover: Boolean): AutoTagOutcome {
    val readings = TrackQueries.of(track)
    val reading = readings.firstOrNull() ?: return AutoTagOutcome.NOT_FOUND
    val looksUpMatch = choices.findsTags || choices.findsCover

    val listed = if (looksUpMatch) {
        findMatches(readings)
        matches.firstOrNull()
    } else {
        null
    }
    val isConfident = listed != null && readings.any { TrackMatcher.score(it, listed) >= TrackMatcher.Confident }
    val match = listed?.let { complete(it) }
    if (listed != null && match != null) {
        val tags = when {
            !choices.findsTags -> original
            choices.fillsBlanksOnly -> original.filledFrom(match.tags)
            else -> original.overlaidWith(match.tags)
        }
        val takesCover = choices.findsCover && (!hasOwnCover || !choices.fillsBlanksOnly && (tags != original || !choices.findsTags))
        takeMatch(tags, coverUrl = if (takesCover) match.coverUrl else "", listed = listed)
    }

    if (choices.findsLyrics) {
        val names = match?.takeIf { isConfident }
        findLyrics(
            query = if (names != null) {
                lyricsQuery(names.title, names.artist, names.tags[TagField.ALBUM].ifBlank { null })
            } else {
                lyricsQuery(reading.title, reading.artist, track.album.takeUnless { it == UnknownNames.ALBUM })
            },
            kept = KeptLyrics,
        )
        val held = LyricsText.of(original[TagField.LYRICS])?.timing
        trustedLyrics
            ?.takeIf { held == null || !choices.fillsBlanksOnly && it.lyrics.timing.ordinal < held.ordinal }
            ?.let(::applyLyrics)
    }

    val matchOutcome = when {
        !looksUpMatch -> null
        matchLookup == MatchLookup.Unreachable -> AutoTagOutcome.UNREACHABLE
        match == null -> AutoTagOutcome.NOT_FOUND
        isConfident -> AutoTagOutcome.READY
        else -> AutoTagOutcome.UNSURE
    }
    return when {
        matchOutcome == AutoTagOutcome.UNREACHABLE -> matchOutcome
        hasChanges -> matchOutcome ?: AutoTagOutcome.READY
        matchOutcome == AutoTagOutcome.NOT_FOUND || matchOutcome == null && lyricsResults.isEmpty() -> AutoTagOutcome.NOT_FOUND
        else -> AutoTagOutcome.UP_TO_DATE
    }
}
