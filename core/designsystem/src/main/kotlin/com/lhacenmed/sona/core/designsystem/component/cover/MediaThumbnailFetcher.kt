package com.lhacenmed.sona.core.designsystem.component.cover

import android.content.ContentResolver
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import coil3.toAndroidUri
import com.lhacenmed.sona.core.common.cover.isMediaFile
import com.lhacenmed.sona.core.common.cover.mediaThumbnailOf
import java.io.File
import kotlin.math.min

/** The side a thumbnail is asked for at when Coil gives it no size. */
private const val FALLBACK_SIZE_PX = 512

/** How finely a thumbnail made here is kept - fine enough that the copy read back looks as the one made did. */
private const val CACHED_THUMBNAIL_QUALITY = 90

/**
 * A track's or a video's cover, wherever one is drawn: Android's own thumbnail of its file
 * ([mediaThumbnailOf]), which it keeps once made - so a list shows every cover at once, where reading each
 * file's picture or decoding a frame of each would lay them out one by one, and again on every launch.
 *
 * A file Android does not index - one addressed by its path, as the Private Folder's are - has no thumbnail
 * kept for it, so the one made from it is kept here instead, in Coil's disk cache: made once, it is read
 * back from then on rather than decoded from the file again each time its row comes into view. It is kept
 * by the file's last change, so a file changed in place is thumbnailed afresh.
 */
class MediaThumbnailFetcher private constructor(
    private val uri: android.net.Uri,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val sizePx = min(options.size.width.pxOrElse { FALLBACK_SIZE_PX }, options.size.height.pxOrElse { FALLBACK_SIZE_PX })
            .coerceAtLeast(1)
        val diskCache = imageLoader.diskCache.takeIf { uri.scheme == ContentResolver.SCHEME_FILE }
            ?: return ImageFetchResult(image = thumbnail(sizePx).asImage(), isSampled = true, dataSource = DataSource.DISK)

        val cacheKey = "thumbnail:${uri.path}:${File(checkNotNull(uri.path)).lastModified()}:$sizePx"
        diskCache.openSnapshot(cacheKey)?.let { snapshot ->
            return SourceFetchResult(
                source = ImageSource(snapshot.data, diskCache.fileSystem, cacheKey, snapshot),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK,
            )
        }
        val thumbnail = thumbnail(sizePx)
        diskCache.openEditor(cacheKey)?.let { editor ->
            runCatching {
                diskCache.fileSystem.write(editor.data) { thumbnail.compress(Bitmap.CompressFormat.JPEG, CACHED_THUMBNAIL_QUALITY, outputStream()) }
                editor.commit()
            }.onFailure { editor.abort() }
        }
        return ImageFetchResult(image = thumbnail.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun thumbnail(sizePx: Int): Bitmap =
        checkNotNull(mediaThumbnailOf(options.context, uri, sizePx)) { "$uri has no thumbnail" }

    /** Takes every track's and video's own address, and leaves any other to the fetchers after it. */
    object Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            data.toAndroidUri().takeIf { it.isMediaFile }?.let { MediaThumbnailFetcher(it, options, imageLoader) }
    }
}
