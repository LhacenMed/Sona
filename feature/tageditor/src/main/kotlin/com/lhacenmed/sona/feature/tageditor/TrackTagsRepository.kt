package com.lhacenmed.sona.feature.tageditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.data.contentUri
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** One file [TrackTagsRepository.saveAll] writes: [tags] into [track]'s file, with the picture [cover] embeds. */
class TrackEdit(val track: Track, val tags: TrackTags, val cover: CoverChoice)

/** The longest side a picture from the device is embedded at - a catalogue's own size. */
private const val DeviceCoverMaxSidePx = 1200

private const val DeviceCoverJpegQuality = 90

/**
 * A track's tags as its file holds them, and the one way they are changed - written into the file, then
 * taken up by every part of the app that shows them. The file is the only place they are kept: its lyrics
 * included, which the player reads from it as they are wanted.
 */
@Singleton
class TrackTagsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaScanner: MediaScanner,
    private val trackFiles: TrackFiles,
    private val lyricsSearchIndex: LyricsSearchIndex,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The ids of the tracks whose files Sona has just written, each as its write is done. */
    private val writtenTrackIds = MutableSharedFlow<Long>(extraBufferCapacity = 16)

    /** What [track]'s file holds. */
    suspend fun read(track: Track): TagFileInfo =
        withContext(ioDispatcher) { TagFile.read(context, track.contentUri, track.path) }

    /** Whether [track]'s file holds a cover of its own - read only where that is wanted, as it loads the pictures. */
    suspend fun hasCover(track: Track): Boolean =
        withContext(ioDispatcher) { TagFile.hasCover(context, track.contentUri, track.path) }

    /** The lyrics [track]'s file holds, as written - blank where it holds none. */
    suspend fun readLyrics(track: Track): String = read(track).tags[TagField.LYRICS]

    /**
     * The lyrics [track]'s file holds - read as this is collected, and again each time Sona writes the file, so
     * what is shown is always what the file holds, whoever wrote it there.
     */
    fun lyrics(track: Track): Flow<String> =
        writtenTrackIds
            .filter { it == track.id }
            .map { }
            .onStart { emit(Unit) }
            .map { readLyrics(track) }

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
        writeFile(track) { TagFile.write(context, track.contentUri, track.path, tags, cover) }
        refreshLibrary(listOf(track))
    }

    /**
     * Writes every one of [edits] into its file, as [save] does, [onWritten] hearing of each - with what kept it out
     * of its file where something did - then has the library read them all back at once: one refresh for the lot,
     * where a file at a time would be one each. Edits sharing a cover are written one after another, so each cover
     * is had once and held no longer than its own files need it.
     */
    suspend fun saveAll(edits: List<TrackEdit>, onWritten: (TrackEdit, Exception?) -> Unit) {
        edits.groupBy { it.cover }.forEach { (choice, sharing) ->
            val cover = try {
                Result.success(coverBytes(choice))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            sharing.forEach { edit ->
                val failure = try {
                    writeFile(edit.track) { TagFile.write(context, edit.track.contentUri, edit.track.path, edit.tags, cover.getOrThrow()) }
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e
                }
                onWritten(edit, failure)
            }
        }
        refreshLibrary(edits.map { it.track })
    }

    /** Writes [lyrics] into [track]'s file as its lyrics, every other tag left as the file holds it, then as [save]. */
    suspend fun saveLyrics(track: Track, lyrics: String) {
        writeFile(track) {
            // Every field is written as given, so the file's own are read first: only the lyrics change.
            val tags = TagFile.read(context, track.contentUri, track.path).tags.with(TagField.LYRICS, lyrics)
            TagFile.write(context, track.contentUri, track.path, tags, cover = null)
        }
        refreshLibrary(listOf(track))
    }

    /**
     * Has [write] change [track]'s file - the player reading on from it as it was - and Android read it again;
     * then tells whatever shows its lyrics to read them again, search among them.
     */
    private suspend fun writeFile(track: Track, write: () -> Unit) {
        withContext(ioDispatcher) {
            trackFiles.write(track.contentUri, write)
            scanFile(track.path)
        }
        writtenTrackIds.emit(track.id)
        lyricsSearchIndex.reindex(track)
    }

    /** Has the library read [tracks]' files back - a file MediaStore has not indexed only by walking storage, which a refresh leaves be. */
    private suspend fun refreshLibrary(tracks: List<Track>) {
        if (tracks.any { it.isManuallyScanned }) mediaScanner.rescan() else mediaScanner.refresh()
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
