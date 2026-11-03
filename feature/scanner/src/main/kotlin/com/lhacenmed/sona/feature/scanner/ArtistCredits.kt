package com.lhacenmed.sona.feature.scanner

/**
 * The artists a credit names - see `LibrarySettings.separateArtists`: a credit is parted at every separator it has,
 * "×", "x", "feat.", "ft.", "featuring", "vs.", ";", a comma, "&", "+" and "/", so "Sidi, Loubabe & Ediye" is three
 * artists. What a credit is read as depends on the credit alone - never on what else the library holds - so it is
 * the same on every track and album naming it, on every scan.
 *
 * A name that only looks like several stays one: a comma, "&" or "+" before "The" - "Tyler, The Creator", "Florence +
 * The Machine" - and the few acts known by such a name, in [NamesKeptWhole].
 */
internal class ArtistCredits(private val separatesArtists: Boolean) {

    /** The artists [credit] names, in the order it names them - [credit] alone while artists are not separated. */
    fun artistsOf(credit: String): List<String> {
        if (!separatesArtists) return listOf(credit)
        return credit.replace(BracketedFeature, " feat. $2").partsAt(JoiningSeparator)
            .flatMap { part -> if (nameKey(part) in NamesKeptWhole) listOf(part) else part.partsAt(ListSeparator) }
            .distinctBy(::nameKey)
            .ifEmpty { listOf(credit) }
    }
}

/** A featured artist in brackets - "Song Artist (feat. Guest)" - read as any other "feat.". */
private val BracketedFeature = Regex("""[(\[]\s*((?:feat|ft)\.?|featuring)\s+([^)\]]+)[)\]]""", RegexOption.IGNORE_CASE)

/** The separators only ever found between artists. */
private val JoiningSeparator = Regex("""\s*(?:;|×|\s[xX]\s|\s(?:feat\.?|ft\.?|featuring|vs\.?)\s)\s*""", RegexOption.IGNORE_CASE)

/** The separators of a list of artists - the Arabic comma among them - unless what follows is "The". */
private val ListSeparator = Regex("""\s*(?:(?:[,،]|\s[&+]\s)(?!\s*the\s)|/)\s*""", RegexOption.IGNORE_CASE)

/** The acts known by a name that reads as a list, by [nameKey]. One more only needs adding here. */
private val NamesKeptWhole = setOf(
    "ac/dc",
    "above & beyond",
    "angus & julia stone",
    "blood, sweat & tears",
    "brooks & dunn",
    "chase & status",
    "crosby, stills & nash",
    "crosby, stills, nash & young",
    "dan + shay",
    "earth, wind & fire",
    "emerson, lake & palmer",
    "hall & oates",
    "mumford & sons",
    "peter, paul and mary",
    "sam & dave",
    "simon & garfunkel",
)

private fun String.partsAt(separator: Regex): List<String> = split(separator).map(String::trim).filter(String::isNotEmpty)
