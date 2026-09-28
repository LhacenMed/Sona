package com.lhacenmed.sona.feature.tageditor.lookup

import java.text.Normalizer

/**
 * What a lookup is after - YTDLnis's `MusicQuery`: the text the catalogues are searched with, and the
 * version the track is. The version is kept apart on purpose: the searched text is the song's name ("Bad
 * Boy"), while the version wanted ("remix", or none) is what tells the right match from a near one.
 */
internal data class TrackQuery(val artist: String, val title: String, val version: String = "") {
    val text: String get() = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Which of the songs the catalogues answered with is the one asked for - YTDLnis's `MusicMatcher`.
 *
 * A catalogue ranks by its own relevance, which is another question: "Marwa Loud Bad Boy" is answered with
 * "Bad Boy (Remix)" as readily as with "Bad Boy". So every catalogue's answers are scored against the query
 * here - title likeness and artist likeness, less a penalty for the wrong version - and the best come first,
 * whichever catalogue they came from. Catalogue order only breaks ties.
 */
internal object TrackMatcher {
    private const val TitleWeight = 0.6
    private const val ArtistWeight = 0.4

    /** The plain song was wanted and this is a version of it. */
    private const val UnwantedVersion = 0.35

    /** A version was wanted and this is the plain song. */
    private const val MissingVersion = 0.30

    /** A version was wanted and this is another one. */
    private const val WrongVersion = 0.45

    /** Enough to keep catalogue order between equals, never enough to outrank a better match. */
    private const val CatalogueStep = 0.02

    /** One name contained in the other ("Zemër" and "Zemër Nena"): still clearly the same song. */
    private const val Contained = 0.9

    /** A best match at least this good answers the query, so no other reading of it is tried. */
    const val Confident = 0.75

    private val Diacritics = Regex("""\p{Mn}+""")
    private val NonAlphanumeric = Regex("""[^\p{L}\p{N}]+""")
    private val Bracketed = Regex("""[(\[][^)\]]*[)\]]""")
    private val Featuring = Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+.+$""", RegexOption.IGNORE_CASE)
    private val Spaces = Regex("""\s+""")

    /** Versions that are another recording, so matching the wrong one is matching the wrong song. */
    private const val Versions =
        "remix|live|acoustic|instrumental|karaoke|unplugged|cover|demo|reprise|orchestral|piano|nightcore|slowed|sped\\s*up"
    private val TrailingVersion = Regex("""\s*(?:[-–—]\s*)?(?:$Versions)\b.*$""", RegexOption.IGNORE_CASE)
    private val VersionWord = Regex("""\b(?:$Versions)\b""", RegexOption.IGNORE_CASE)

    /** Case, accents and punctuation are noise here: "Zemër" and "zemer" are the same song. */
    fun normalize(value: String): String =
        Diacritics.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
            .let { NonAlphanumeric.replace(it, " ") }
            .trim()
            .lowercase()

    /**
     * The song's name alone - no featured artists, brackets or version. A version word only marks a version
     * when a name is left before it, so "Piano Man" keeps its whole name while "Bad Boy (Remix)" is the song
     * the remix is of.
     */
    fun baseTitle(title: String): String {
        val bare = Bracketed.replace(Featuring.replace(title, ""), "")
        return normalize(TrailingVersion.replace(bare, "")).ifBlank { normalize(bare) }.ifBlank { normalize(title) }
    }

    /** The version a title names, blank for the plain song - only what trails the name counts. */
    fun versionOf(title: String): String {
        val tail = normalize(title).removePrefix(baseTitle(title)).trim()
        return VersionWord.find(tail)?.value?.replace(Spaces, " ").orEmpty()
    }

    /** How well [match] answers [query], from 0 (unrelated) to 1 (exactly the song asked for). */
    fun score(query: TrackQuery, match: CatalogueMatch): Double {
        val titleScore = similarity(baseTitle(query.title), baseTitle(match.title))
        val value = if (query.artist.isBlank()) {
            titleScore
        } else {
            TitleWeight * titleScore + ArtistWeight * similarity(normalize(query.artist), normalize(match.artist))
        }
        return (value - versionPenalty(query.version, versionOf(match.title))).coerceIn(0.0, 1.0)
    }

    /**
     * Every catalogue's answers as one list, best first. [byCatalogue] is in catalogue order, and a song more
     * than one catalogue knows is kept once, from the one that described it best.
     */
    fun rank(query: TrackQuery, byCatalogue: List<List<CatalogueMatch>>, limit: Int): List<CatalogueMatch> =
        byCatalogue.flatMapIndexed { rank, matches ->
            val bias = CatalogueStep * (byCatalogue.size - rank)
            matches.map { it to score(query, it) + bias }
        }
            .sortedByDescending { (_, score) -> score }
            .distinctBy { (match, _) -> listOf(normalize(match.artist), baseTitle(match.title), versionOf(match.title)) }
            .take(limit)
            .map { (match, _) -> match }

    private fun versionPenalty(wanted: String, found: String): Double = when {
        wanted == found -> 0.0
        wanted.isBlank() -> UnwantedVersion
        found.isBlank() -> MissingVersion
        else -> WrongVersion
    }

    /** Edit-distance likeness, with one name inside the other taken as a near match. */
    fun similarity(a: String, b: String): Double = when {
        a == b -> 1.0
        a.isEmpty() || b.isEmpty() -> 0.0
        a.contains(b) || b.contains(a) -> Contained
        else -> 1.0 - distance(a, b).toDouble() / maxOf(a.length, b.length)
    }

    /** Levenshtein over two rows: these are song names, so it stays cheap. */
    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
