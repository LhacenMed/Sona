package com.lhacenmed.sona.feature.tageditor.lookup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Finds a track's tags in the catalogues - YTDLnis's `MusicMetadataUtil`: every [Catalogue] is asked at once,
 * and their answers ranked together by how well each fits the track.
 */
internal object TagLookup {
    /** Each catalogue's answers kept after ranking - enough that narrowing the list to one catalogue still leaves a choice. */
    private const val MatchesPerCatalogue = 8

    /** The catalogues, in the order that decides between two equally good matches. Adding one is adding it here. */
    private val catalogues = listOf(DeezerCatalogue, ItunesCatalogue)

    val catalogueNames: List<String> = catalogues.map { it.name }

    /**
     * The songs [queries] are answered with, best first - null when no catalogue could be reached, the one
     * outcome worth asking again for.
     *
     * The readings are tried in order, but only until one is answered convincingly: another reading of the
     * track is worth a request only while the last left the song in doubt.
     */
    suspend fun search(queries: List<TrackQuery>): List<CatalogueMatch>? = withContext(Dispatchers.IO) {
        var best: List<CatalogueMatch>? = null
        var isAnswered = false
        for (query in queries) {
            val matches = lookup(query) ?: continue
            isAnswered = true
            val top = matches.firstOrNull() ?: continue
            if (best == null) best = matches
            if (TrackMatcher.score(query, top) >= TrackMatcher.Confident) {
                best = matches
                break
            }
        }
        if (isAnswered) best.orEmpty() else null
    }

    /** [match] with every tag its catalogue knows - the fields a search leaves out, asked for the match shown. */
    suspend fun complete(match: CatalogueMatch): CatalogueMatch = withContext(Dispatchers.IO) {
        catalogues.firstOrNull { it.name == match.catalogue }?.details(match) ?: match
    }

    /** One round: every catalogue at once, so asking them all costs the slowest rather than their sum. */
    private suspend fun lookup(query: TrackQuery): List<CatalogueMatch>? {
        val answers = coroutineScope { catalogues.map { async { it.search(query.text, MatchesPerCatalogue) } }.awaitAll() }
        if (answers.all { it == null }) return null
        return TrackMatcher.rank(query, answers.map { it.orEmpty() }, MatchesPerCatalogue * catalogues.size)
    }
}
