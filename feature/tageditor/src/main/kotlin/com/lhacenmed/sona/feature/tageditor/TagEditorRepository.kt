package com.lhacenmed.sona.feature.tageditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.data.lyrics.LyricsRepository
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.TrackFiles
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.feature.tageditor.tags.TagFile
import com.lhacenmed.sona.feature.tageditor.tags.TagFileInfo
import com.lhacenmed.sona.feature.tageditor.tags.TrackTags
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** The longest side a picture from the device is embedded at - a catalogue's own size. */
private const val DeviceCoverMaxSidePx = 1200

private const val DeviceCoverJpegQuality = 90

/**
 * A track's tags as its file holds them, and the one way they are changed - written into the file, then
 * taken up by every part of the app that shows them.
 */
@Singleton
class TagEditorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaScanner: MediaScanner,
    private val trackFiles: TrackFiles,
    private val lyricsRepository: LyricsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * What [track]'s file holds - its lyrics as the player shows them, which are the ones the user typed in
     * where they did, so saving writes those into the file rather than losing them.
     */
    suspend fun read(track: Track): TagFileInfo =
        withContext(ioDispatcher) {
            val info = TagFile.read(context, track.contentUri, track.path)
            val typedLyrics = lyricsRepository.typedLyrics(track.id) ?: return@withContext info
            info.copy(tags = info.tags.with(TagField.LYRICS, typedLyrics))
        }

    /**
     * The picture [choice] embeds, ready to write: a catalogue's downloaded, the device's scaled down - or null
     * for the file's own, which is left as it is. Throws when it cannot be had.
     */
    suspend fun coverBytes(choice: CoverChoice): ByteArray? =
        withContext(ioDispatcher) {
            when (choice) {
                CoverChoice.Own -> null
                is CoverChoice.Web -> checkNotNull(Http.bytes(choice.url)) { "The cover could not be downloaded" }
                is CoverChoice.Device -> deviceCover(Uri.parse(choice.uri))
            }
        }

    /**
     * Writes [tags] into [track]'s file, with [cover] when there is one - see [coverBytes] - then waits for the
     * library to have read them back, so every list, the player and the lyrics show the new tags the moment
     * this returns: the file alone is read again, and the library re-reads MediaStore - walking storage only
     * for a file MediaStore has not indexed. A track the player is on reads on from the file as it was - see [TrackFiles] - so it carries on
     * without a break. Throws when the file cannot be written, a [SecurityException] among others where
     * Android has not granted it.
     */
    suspend fun save(track: Track, tags: TrackTags, cover: ByteArray?) {
        withContext(ioDispatcher) {
            trackFiles.write(track.contentUri) { TagFile.write(context, track.contentUri, track.path, tags, cover) }
            scanFile(track.path)
            lyricsRepository.forgetLyrics(track.id)
        }
        // A file MediaStore has not indexed is only read again by walking storage, which a refresh leaves be.
        if (track.isManuallyScanned) mediaScanner.rescan() else mediaScanner.refresh()
    }

    /** The picture at [uri] as a JPEG no larger than [DeviceCoverMaxSidePx] - a photo is many times what a cover needs. */
    private fun deviceCover(uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= DeviceCoverMaxSidePx) sampleSize *= 2
        val sampled = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("The picture could not be read")
        val scale = DeviceCoverMaxSidePx.toFloat() / maxOf(sampled.width, sampled.height)
        val cover = if (scale < 1f) {
            Bitmap.createScaledBitmap(sampled, (sampled.width * scale).toInt(), (sampled.height * scale).toInt(), true)
        } else {
            sampled
        }
        return ByteArrayOutputStream().use { output ->
            cover.compress(Bitmap.CompressFormat.JPEG, DeviceCoverJpegQuality, output)
            output.toByteArray()
        }
    }

    /** Has Android read the file again, so MediaStore - which the library follows - carries its new tags. */
    private suspend fun scanFile(path: String) = suspendCancellableCoroutine { continuation ->
        MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> continuation.resume(Unit) }
    }
}
