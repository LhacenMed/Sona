package com.lhacenmed.sona.feature.tageditor.lookup

import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.UnknownNames
import java.io.File

/**
 * The ways a track can be looked up, best first - YTDLnis's title parsing, turned on a library track.
 *
 * A track with an artist is looked up by its own tags, its title cleaned of what catalogues do not name
 * ("(Official Video)", "ft. …"). Its title, and then its file's name, are also read the way a video's title
 * is - "Artist - Title", or "Title - Artist1 & Artist2" - since a file that came from the web often carries
 * its real names there and nothing useful in its tags.
 */
internal object TrackQueries {
    private val FeaturingInArtist = Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+.+$""", RegexOption.IGNORE_CASE)
    private val FeaturingInTitle = listOf(
        Regex("""\s*[(\[]\s*(?:ft\.?|feat\.?|featuring)\s+[^)\]]+[)\]]""", RegexOption.IGNORE_CASE),
        Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+.+$""", RegexOption.IGNORE_CASE),
    )

    /** What a video's title tends to carry in brackets that no catalogue names. */
    private val BracketNoise = Regex(
        """[(\[]\s*(?:""" +
            """official\s*(?:music\s*|lyric\s*)?(?:video|audio|mv)?|""" +
            """lyrics?\s*(?:video)?|""" +
            """audio|hd|hq|4k|uhd|""" +
            """remaster(?:ed)?(?:\s+\d+)?|""" +
            """visuali[sz]er|tiktok|""" +
            """slowed(?:\s*[+&]\s*reverb)?|sped[\s-]*up|nightcore|""" +
            """radio[\s-]*edit|""" +
            """(?:piano|violin|guitar|acoustic|orchestral)\s*(?:version|cover|solo)?|""" +
            """(?:\w[\w\s]*?\s+)?cover(?:\s+by\s+[^)]+)?|""" +
            """(?:\w[\w\s]*?\s+)?remix|""" +
            """(?:album|single|extended|clean|explicit|radio)\s*(?:version|mix|edit)?|""" +
            """ost|version|edit|music\s+video""" +
            """)\s*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val ArtistSplit = Regex("""\s*,\s*|\s*&\s*|\s+[xX]\s+""")
    private val TitleSplit = Regex("""\s*[-–—|]\s*""")
    private val TrailingDescription = listOf(Regex("""\s*\|\s*.+$"""), Regex("""//\s*.+$"""))
    private val ExtraSpaces = Regex("""\s{2,}""")

    /** Every distinct reading of the track, best first. [artist] is blank where the track has none. */
    fun of(title: String, artist: String, fileName: String): List<TrackQuery> =
        buildList {
            if (artist.isNotBlank() && title.isNotBlank()) add(query(formatArtists(parseArtists(artist)), title))
            addAll(fromCombined(title))
            addAll(fromCombined(fileName))
            if (isEmpty() && title.isNotBlank()) add(query("", title))
        }.filter { it.text.isNotBlank() }.distinctBy { it.text.lowercase() }

    /** Every distinct reading of [track], best first: its own names - an unknown artist as none - then its file's. */
    fun of(track: Track): List<TrackQuery> =
        of(
            title = track.title,
            artist = track.artist.takeUnless { it == UnknownNames.ARTIST }.orEmpty(),
            fileName = File(track.path).nameWithoutExtension,
        )

    /** A title cleaned of featuring credits, bracketed noise and trailing descriptions. */
    fun cleanTitle(raw: String): String {
        var cleaned = raw
        FeaturingInTitle.forEach { cleaned = it.replace(cleaned, "") }
        cleaned = BracketNoise.replace(cleaned, "")
        TrailingDescription.forEach { cleaned = it.replace(cleaned, "") }
        return ExtraSpaces.replace(cleaned, " ").trim().trimEnd('-', '|', ' ', ',')
    }

    /** "Artist - Title" read both ways round; nothing for a text with no separator in it. */
    private fun fromCombined(raw: String): List<TrackQuery> {
        val parts = raw.split(TitleSplit, limit = 2)
        if (parts.size != 2) return emptyList()
        val left = parts[0].trim()
        val right = parts[1].trim()
        return buildList {
            add(query(formatArtists(parseArtists(left)), right))
            if (ArtistSplit.containsMatchIn(right) && !ArtistSplit.containsMatchIn(left) && '(' !in right) {
                add(query(formatArtists(parseArtists(cleanTitle(right))), left))
            }
        }
    }

    private fun parseArtists(raw: String): List<String> =
        ArtistSplit.split(FeaturingInArtist.replace(raw, "").trim()).map { it.trim() }.filter { it.isNotEmpty() }

    // Three artists or more is too much to match on, so the first stands for them.
    private fun formatArtists(artists: List<String>): String = when {
        artists.size == 2 -> "${artists[0]} & ${artists[1]}"
        else -> artists.firstOrNull().orEmpty()
    }

    private fun query(artist: String, rawTitle: String) =
        TrackQuery(artist, cleanTitle(rawTitle), TrackMatcher.versionOf(rawTitle))
}
