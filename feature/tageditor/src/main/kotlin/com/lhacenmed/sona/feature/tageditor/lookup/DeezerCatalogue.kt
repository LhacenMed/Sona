package com.lhacenmed.sona.feature.tageditor.lookup

import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.firstNonBlank
import com.lhacenmed.sona.feature.tageditor.net.objects
import com.lhacenmed.sona.feature.tageditor.net.string
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.feature.tageditor.tags.TrackTags
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Deezer, the first catalogue - YTDLnis's `DeezerProvider`: it knows releases outside the English-speaking
 * world well and needs no key, so it wins wherever another catalogue answers just as well.
 *
 * Its search carries the song, the artist, the album and the cover. The year and the numbering are on the
 * track, the genre and the album artist on the album, so [details] reads both, at once.
 */
internal object DeezerCatalogue : Catalogue {
    private const val Api = "https://api.deezer.com"

    override val name = "Deezer"

    override suspend fun search(query: String, limit: Int): List<CatalogueMatch>? =
        Http.json("$Api/search?q=${Http.encode(query)}&limit=$limit")
            ?.objects("data")
            ?.map { it.toMatch() }
            ?.filter { it.isUsable }

    override suspend fun details(match: CatalogueMatch): CatalogueMatch = coroutineScope {
        val track = async { Http.json("$Api/track/${match.trackId}") }
        val album = async { Http.json("$Api/album/${match.albumId}") }
        match.completedWith(track.await(), album.await())
    }

    private fun JSONObject.toMatch(): CatalogueMatch {
        val album = optJSONObject("album")
        val cover = album?.string("cover_xl").orEmpty()
        return CatalogueMatch(
            tags = TrackTags()
                .with(TagField.TITLE, string("title"))
                .with(TagField.ARTIST, optJSONObject("artist")?.string("name").orEmpty())
                .with(TagField.ALBUM, album?.string("title").orEmpty()),
            coverUrl = cover,
            thumbnailUrl = CoverSizes.thumbnailOf(cover),
            catalogue = name,
            trackId = string("id"),
            albumId = album?.string("id").orEmpty(),
        )
    }

    /** Either resource may be missing: whichever answered adds what it knows. */
    private fun CatalogueMatch.completedWith(track: JSONObject?, album: JSONObject?): CatalogueMatch = copy(
        tags = tags
            .with(TagField.YEAR, firstNonBlank(track?.string("release_date"), album?.string("release_date")).take(4))
            .with(TagField.ALBUM_ARTIST, album?.optJSONObject("artist")?.string("name").orEmpty())
            .with(TagField.GENRE, album?.optJSONObject("genres")?.objects("data")?.firstOrNull()?.string("name").orEmpty())
            .with(TagField.TRACK_NUMBER, track?.string("track_position").orEmpty())
            .with(TagField.TRACK_TOTAL, album?.string("nb_tracks").orEmpty())
            .with(TagField.DISC_NUMBER, track?.string("disk_number").orEmpty()),
    )
}
