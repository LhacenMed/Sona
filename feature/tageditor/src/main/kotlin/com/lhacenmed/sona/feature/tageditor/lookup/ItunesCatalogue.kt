package com.lhacenmed.sona.feature.tageditor.lookup

import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.firstNonBlank
import com.lhacenmed.sona.feature.tageditor.net.objects
import com.lhacenmed.sona.feature.tageditor.net.string
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.feature.tageditor.tags.TrackTags
import org.json.JSONObject

/**
 * iTunes, asked alongside Deezer - YTDLnis's `ItunesProvider`: it often names the plain release where Deezer
 * ranks a version of it first, so it is asked every time rather than only when Deezer has nothing.
 *
 * Its search returns the whole record in one answer, so there is nothing left for [details] to fill in.
 */
internal object ItunesCatalogue : Catalogue {
    private const val Api = "https://itunes.apple.com"

    override val name = "iTunes"

    override suspend fun search(query: String, limit: Int): List<CatalogueMatch>? =
        Http.json("$Api/search?term=${Http.encode(query)}&entity=song&limit=$limit")
            ?.objects("results")
            ?.map { it.toMatch() }
            ?.filter { it.isUsable }

    private fun JSONObject.toMatch(): CatalogueMatch {
        val artwork = string("artworkUrl100")
        return CatalogueMatch(
            tags = TrackTags()
                .with(TagField.TITLE, string("trackName"))
                .with(TagField.ARTIST, string("artistName"))
                .with(TagField.ALBUM, string("collectionName"))
                .with(TagField.YEAR, string("releaseDate").take(4))
                // A compilation names its album artist; a single artist's release leaves it out.
                .with(TagField.ALBUM_ARTIST, firstNonBlank(string("collectionArtistName"), string("artistName")))
                .with(TagField.GENRE, string("primaryGenreName"))
                .with(TagField.TRACK_NUMBER, string("trackNumber"))
                .with(TagField.TRACK_TOTAL, string("trackCount"))
                .with(TagField.DISC_NUMBER, string("discNumber")),
            coverUrl = CoverSizes.fullOf(artwork),
            thumbnailUrl = CoverSizes.thumbnailOf(artwork),
            catalogue = name,
        )
    }
}
