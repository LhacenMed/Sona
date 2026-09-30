package com.lhacenmed.sona.core.designsystem.component.cover

import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import coil3.toAndroidUri
import com.lhacenmed.sona.core.common.cover.isMediaFile
import com.lhacenmed.sona.core.common.cover.mediaThumbnailOf
import kotlin.math.min

/** The side a thumbnail is asked for at when Coil gives it no size. */
private const val FALLBACK_SIZE_PX = 512

/**
 * A track's or a video's cover, wherever one is drawn: Android's own thumbnail of its file
 * ([mediaThumbnailOf]), which it keeps once made - so a list shows every cover at once, where reading each
 * file's picture or decoding a frame of each would lay them out one by one, and again on every launch.
 */
class MediaThumbnailFetcher private constructor(
    private val uri: android.net.Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val sizePx = min(options.size.width.pxOrElse { FALLBACK_SIZE_PX }, options.size.height.pxOrElse { FALLBACK_SIZE_PX })
        val thumbnail = checkNotNull(mediaThumbnailOf(options.context, uri, sizePx.coerceAtLeast(1))) {
            "$uri has no thumbnail"
        }
        return ImageFetchResult(image = thumbnail.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    /** Takes every track's and video's own address, and leaves any other to the fetchers after it. */
    object Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            data.toAndroidUri().takeIf { it.isMediaFile }?.let { MediaThumbnailFetcher(it, options) }
    }
}
