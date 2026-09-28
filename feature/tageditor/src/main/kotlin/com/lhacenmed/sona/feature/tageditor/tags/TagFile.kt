package com.lhacenmed.sona.feature.tageditor.tags

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.id3.valuepair.ImageFormats

/** What the editor reads of a file: its [tags], and its [bitrateKbps] where it can be told. */
data class TagFileInfo(val tags: TrackTags, val bitrateKbps: Long?)

/**
 * The tags inside an audio file - read, and written back - YTDLnis's `MusicTagUtil`.
 *
 * Every call blocks, and is made off the main thread.
 */
internal object TagFile {

    init {
        // Artwork through Android's own image classes: the desktop ones jaudiotagger defaults to are not here.
        TagOptionSingleton.getInstance().isAndroid = true
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    /** The tags the file at [path] holds, and its bitrate - neither, for a file that cannot be read. */
    fun read(path: String): TagFileInfo = runCatching {
        val audioFile = AudioFileIO.read(File(path))
        val tag = audioFile.tag
        val tags = if (tag == null) {
            TrackTags()
        } else {
            TagField.entries.fold(TrackTags()) { tags, field ->
                tags.with(field, runCatching { tag.getFirst(field.key) }.getOrDefault("").trim())
            }
        }
        TagFileInfo(tags, audioFile.audioHeader?.bitRateAsNumber?.takeIf { it > 0 })
    }.getOrDefault(TagFileInfo(TrackTags(), bitrateKbps = null))

    /**
     * Writes [tags] - and [cover], when one was chosen - into the file [target] opens, whose name ends in
     * [extension], which is what the tag format is told by.
     *
     * The file is tagged as a copy in [context]'s cache, then written back over itself through [target], so
     * the one write the original ever sees is a whole, finished file - and one Android granted through a
     * content uri, rather than a path, can be written at all. A field left blank is taken out of the file;
     * one its format cannot hold is left as it was.
     */
    fun write(context: Context, target: Uri, extension: String, tags: TrackTags, cover: ByteArray?) {
        val resolver = context.contentResolver
        val workingCopy = File.createTempFile("tags", ".$extension", context.cacheDir)
        try {
            checkNotNull(resolver.openInputStream(target)) { "Cannot read $target" }
                .use { input -> workingCopy.outputStream().use(input::copyTo) }

            val audioFile = AudioFileIO.read(workingCopy)
            val tag = audioFile.tagOrCreateAndSetDefault
            TagField.entries.forEach { field ->
                val value = tags[field]
                runCatching { if (value.isBlank()) tag.deleteField(field.key) else tag.setField(field.key, value) }
            }
            if (cover != null) {
                tag.deleteArtworkField()
                tag.setField(artworkOf(cover))
            }
            AudioFileIO.write(audioFile)

            checkNotNull(resolver.openOutputStream(target, "wt")) { "Cannot write $target" }
                .use { output -> workingCopy.inputStream().use { it.copyTo(output) } }
        } finally {
            workingCopy.delete()
        }
    }

    /** [bytes] as the front cover. */
    private fun artworkOf(bytes: ByteArray): Artwork =
        ArtworkFactory.getNew().apply {
            binaryData = bytes
            mimeType = ImageFormats.getMimeTypeForBinarySignature(bytes)
            pictureType = PictureTypes.DEFAULT_ID
        }
}
