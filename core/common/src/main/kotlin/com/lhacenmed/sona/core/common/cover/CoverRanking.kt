package com.lhacenmed.sona.core.common.cover

/**
 * Every distinct cover among [coverArtUris], the most shared first: Auxio's `CoverCollection.from`.
 *
 * This is the order a composed cover draws in, so it has to come out the same every time it is
 * computed - covers shared equally therefore keep Auxio's tiebreak, the later address first, rather
 * than whatever order the rows arrived in.
 */
fun rankedCoverArtUris(coverArtUris: Iterable<String?>): List<String> =
    rankedCoverArtUris(
        coverArtUris.filterNotNull().filter { it.isNotEmpty() }.groupingBy { it }.eachCount(),
    )

/** The same ranking, for covers a query has already counted rather than a list to be counted here. */
fun rankedCoverArtUris(coverArtUriCounts: Map<String, Int>): List<String> =
    coverArtUriCounts.entries
        .sortedByDescending { it.key }
        .sortedByDescending { it.value }
        .map { it.key }
