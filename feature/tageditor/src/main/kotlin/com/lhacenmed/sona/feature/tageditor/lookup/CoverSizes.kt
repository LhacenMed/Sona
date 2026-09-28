package com.lhacenmed.sona.feature.tageditor.lookup

/**
 * The sizes a catalogue's cover is fetched at - YTDLnis's `CatalogueImageSource` artwork table. Each
 * catalogue serves its artwork at the size written into its address, so the size to embed and a small copy
 * to draw are both a rewrite of the one address away, with nothing to ask.
 */
internal object CoverSizes {
    private class Artwork(val host: String, val size: Regex, val full: String, val thumbnail: String)

    private val Artworks = listOf(
        // Large enough for any screen, small enough to embed in every track without weighing the file down.
        Artwork("mzstatic.com", Regex("""/\d+x\d+bb\.jpg$"""), "/1200x1200bb.jpg", "/300x300bb.jpg"),
        Artwork("dzcdn.net", Regex("""/\d+x\d+-"""), "/1000x1000-", "/250x250-"),
    )

    fun fullOf(url: String): String = rewrite(url) { it.full }

    fun thumbnailOf(url: String): String = rewrite(url) { it.thumbnail }

    private fun rewrite(url: String, size: (Artwork) -> String): String {
        val artwork = Artworks.firstOrNull { it.host in url } ?: return url
        return artwork.size.replace(url, size(artwork))
    }
}
