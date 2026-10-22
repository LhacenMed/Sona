package com.lhacenmed.sona.feature.scanner

/**
 * The artists each credit of a library names - see `LibrarySettings.separateArtists`.
 *
 * Some separators only ever join artists: "×", "x", "feat.", "ft.", "featuring", "vs." and ";". The others are
 * as often part of one name - "Earth, Wind & Fire", "Simon & Garfunkel", "AC/DC", "Florence + The Machine" - so
 * a credit is parted at a comma, "&", "+", "/" or "and" only where one of the names that leaves is credited on
 * its own somewhere among [credits]: "Justin Bieber, Benny Blanco & Billie Eilish" is three artists wherever
 * Billie Eilish has a credit of her own, while no Simon or Garfunkel ever does.
 *
 * Read from every credit of one scan, so a credit is parted the same way on every track and album naming it.
 */
internal class ArtistCredits(credits: Collection<String>, private val separatesArtists: Boolean) {

    /** Every name credited alone - a whole credit, or one of the parts only a joining separator leaves. */
    private val soloNames: Set<String> =
        if (separatesArtists) credits.flatMapTo(HashSet()) { credit -> joinedParts(credit).map(::nameKey) } else emptySet()

    /** The artists [credit] names, in the order it names them - [credit] alone while artists are not separated. */
    fun artistsOf(credit: String): List<String> {
        if (!separatesArtists) return listOf(credit)
        return joinedParts(credit)
            .flatMap { part ->
                val names = ambiguousParts(part)
                if (names.size > 1 && names.any { nameKey(it) in soloNames }) names else listOf(part)
            }
            .distinctBy(::nameKey)
            .ifEmpty { listOf(credit) }
    }
}

/** A featured artist in brackets - "Song Artist (feat. Guest)" - read as any other "feat.". */
private val BracketedFeature = Regex("""[(\[]\s*((?:feat|ft)\.?|featuring)\s+([^)\]]+)[)\]]""", RegexOption.IGNORE_CASE)

/** The separators only ever found between artists. */
private val JoiningSeparator = Regex("""\s*(?:;|×|\s[xX]\s|\s(?:feat\.?|ft\.?|featuring|vs\.?)\s)\s*""", RegexOption.IGNORE_CASE)

/** The separators as often found inside one artist's name. */
private val AmbiguousSeparator = Regex("""\s*(?:,|/|\s(?:&|\+|and)\s)\s*""", RegexOption.IGNORE_CASE)

private fun joinedParts(credit: String): List<String> =
    credit.replace(BracketedFeature, " feat. $2").partsAt(JoiningSeparator)

private fun ambiguousParts(part: String): List<String> = part.partsAt(AmbiguousSeparator)

private fun String.partsAt(separator: Regex): List<String> = split(separator).map(String::trim).filter(String::isNotEmpty)
