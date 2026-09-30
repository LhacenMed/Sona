package com.lhacenmed.sona.feature.tageditor.tags

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kyant.taglib.AudioPropertiesReadStyle
import com.kyant.taglib.Picture
import com.kyant.taglib.PropertyMap
import com.kyant.taglib.TagLib
import java.io.File

/** What the editor reads of a file: its [tags], and its [bitrateKbps] where it can be told. */
data class TagFileInfo(val tags: TrackTags, val bitrateKbps: Long?)

/** The trailing ID3v1 tag TagLib writes after MPEG audio: "TAG" and 125 bytes more. */
private const val ID3V1_SIZE = 128

/**
 * The tags inside an audio or video file - read, and written back in place - through TagLib: MP3 and AAC,
 * MP4 and M4A, FLAC, Ogg Vorbis and Opus, Matroska and WebM, WMA, APE, WavPack, WAV, AIFF and DSF among them.
 *
 * TagLib picks a file's format by its name before its content, so each file is first told by its content
 * ([AudioContainer]): one named for what it holds is read and written in place, and one that is not - an MP4
 * saved as `.mp3` - through a copy named for what it holds, written back whole. Such a file written by name
 * before had an ID3 tag put in front of its MP4 and another after it, which no player reads and which moves
 * the audio the MP4 finds by its offsets; its next save takes both off, leaving the file as it was with its
 * tags where they belong.
 *
 * TagLib takes every descriptor it is handed as its own and closes it, so each call is given a duplicate.
 * Every call blocks, and is made off the main thread.
 */
internal object TagFile {

    /** The tags the file at [uri] - [path] on storage - holds, and its bitrate; neither, for one that cannot be read. */
    fun read(context: Context, uri: Uri, path: String): TagFileInfo = runCatching {
        val layout = layoutOf(context, uri, path)
        if (!layout.needsCopy) return@runCatching open(context, uri, "r").use(::readTags)
        val proper = withCopy(context, uri, layout) { copy -> ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY).use(::readTags) }
        // What was typed into the misplaced tag is the latest the user gave: it goes where it belongs on save.
        if (!layout.hasForeignId3) proper else proper.copy(tags = proper.tags.overlaidWith(open(context, uri, "r").use(::readTags).tags))
    }.getOrDefault(TagFileInfo(TrackTags(), bitrateKbps = null))

    /**
     * Writes [tags] - and [cover], when one was chosen - into the file at [uri], [path] on storage: through a
     * content uri, so a file Android granted that way rather than by its path can be written at all.
     *
     * Only the fields [tags] changes are touched, so whatever else the file holds - a second artist, tags the
     * editor does not show - stays as it was. A field left blank is taken out of the file.
     */
    fun write(context: Context, uri: Uri, path: String, tags: TrackTags, cover: ByteArray?) {
        val layout = layoutOf(context, uri, path)
        if (!layout.needsCopy) {
            open(context, uri, "rw").use { writeTags(it, tags, cover) }
            return
        }
        withCopy(context, uri, layout) { copy ->
            ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_WRITE).use { writeTags(it, tags, cover) }
            writeBack(context, uri, copy)
        }
    }

    private fun readTags(file: ParcelFileDescriptor): TagFileInfo {
        val properties = TagLib.getMetadata(file.dup().detachFd(), readPictures = false)?.propertyMap
        val bitrate = TagLib.getAudioProperties(file.dup().detachFd(), AudioPropertiesReadStyle.Fast)?.bitrate
        return TagFileInfo(properties?.let(::tagsOf) ?: TrackTags(), bitrate?.toLong()?.takeIf { it > 0 })
    }

    private fun writeTags(file: ParcelFileDescriptor, tags: TrackTags, cover: ByteArray?) {
        val properties = checkNotNull(TagLib.getMetadata(file.dup().detachFd(), readPictures = false)) {
            "This file's format holds no tags"
        }.propertyMap
        val held = tagsOf(properties)
        val changed = TagField.entries.filter { tags[it] != held[it] }
        if (changed.isNotEmpty()) {
            changed.forEach { properties.write(it, tags) }
            check(TagLib.savePropertyMap(file.dup().detachFd(), properties)) { "The tags could not be written into this file" }
        }
        if (cover != null) {
            val picture = Picture(data = cover, description = "", pictureType = "Front Cover", mimeType = imageMimeTypeOf(cover))
            check(TagLib.savePictures(file.dup().detachFd(), arrayOf(picture))) { "This file's format cannot hold a cover" }
        }
    }

    /** How the file at [uri] is laid out, told by its first bytes, next to what its [path] names it. */
    private class Layout(
        val container: AudioContainer?,
        val named: AudioContainer?,
        /** The bytes of an ID3v2 tag in front of a container that holds none of its own - TagLib's, misplaced. */
        val foreignId3Size: Long,
        val size: Long,
    ) {
        val hasForeignId3: Boolean get() = foreignId3Size > 0

        /** Whether TagLib, going by the name, would take the file for something it is not. */
        val needsCopy: Boolean get() = hasForeignId3 || container != null && named != null && container != named
    }

    private fun layoutOf(context: Context, uri: Uri, path: String): Layout {
        val size = open(context, uri, "r").use { it.statSize }
        val (head, id3Size) = checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read $uri" }.use { input ->
            val start = input.readNBytesCompat(10)
            val id3Size = id3v2SizeOf(start)
            if (id3Size > 0) input.skipFully(id3Size - start.size)
            (if (id3Size > 0) input.readNBytesCompat(16) else start + input.readNBytesCompat(6)) to id3Size
        }
        val container = AudioContainer.of(head, hadId3 = id3Size > 0)
        val foreign = id3Size > 0 && container != null && container != AudioContainer.MPEG && container != AudioContainer.FLAC
        return Layout(container, AudioContainer.named(path.substringAfterLast('.', "")), if (foreign) id3Size else 0L, size)
    }

    /**
     * Runs [block] on a copy of the file at [uri], named for what [layout] says it holds - without the tags
     * misplaced around it - and lets the copy go after.
     */
    private fun <T> withCopy(context: Context, uri: Uri, layout: Layout, block: (File) -> T): T {
        val dir = File(context.cacheDir, "tag-edit").apply { mkdirs() }
        val copy = File.createTempFile("tags", ".${checkNotNull(layout.container).extension}", dir)
        try {
            checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read $uri" }.use { input ->
                input.skipFully(layout.foreignId3Size)
                var length = layout.size - layout.foreignId3Size
                if (layout.hasForeignId3 && endsWithId3v1(context, uri, layout.size)) length -= ID3V1_SIZE
                copy.outputStream().use { output -> input.copyExactly(output, length) }
            }
            return block(copy)
        } finally {
            copy.delete()
        }
    }

    /** Replaces what the file at [uri] holds with [copy], cut to its length only once all of it is written. */
    private fun writeBack(context: Context, uri: Uri, copy: File) {
        ParcelFileDescriptor.AutoCloseOutputStream(open(context, uri, "rw")).channel.use { output ->
            copy.inputStream().channel.use { input ->
                var written = 0L
                while (written < input.size()) written += input.transferTo(written, input.size() - written, output)
            }
            output.truncate(copy.length())
        }
    }

    private fun open(context: Context, uri: Uri, mode: String): ParcelFileDescriptor =
        checkNotNull(context.contentResolver.openFileDescriptor(uri, mode)) { "Cannot open $uri" }

    /** The size of the ID3v2 tag [head] starts with, header and footer included, or 0 for none. */
    private fun id3v2SizeOf(head: ByteArray): Long {
        if (head.size < 10 || head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) return 0L
        val body = (6..9).fold(0L) { size, index -> (size shl 7) or (head[index].toLong() and 0x7F) }
        val hasFooter = head[5].toInt() and 0x10 != 0
        return 10 + body + if (hasFooter) 10 else 0
    }

    private fun endsWithId3v1(context: Context, uri: Uri, size: Long): Boolean {
        if (size < ID3V1_SIZE) return false
        return checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read $uri" }.use { input ->
            input.skipFully(size - ID3V1_SIZE)
            input.readNBytesCompat(3).contentEquals("TAG".toByteArray(Charsets.ISO_8859_1))
        }
    }

    private fun java.io.InputStream.readNBytesCompat(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = read(buffer, read, count - read)
            if (n < 0) break
            read += n
        }
        return if (read == count) buffer else buffer.copyOf(read)
    }

    private fun java.io.InputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                check(read() >= 0) { "The file ended early" }
                left--
            } else {
                left -= skipped
            }
        }
    }

    private fun java.io.InputStream.copyExactly(output: java.io.OutputStream, count: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
        var left = count
        while (left > 0) {
            val n = read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            check(n >= 0) { "The file ended early" }
            output.write(buffer, 0, n)
            left -= n
        }
    }

    /**
     * [properties] as the editor's fields. A track number may carry its count - "3/12" - where the format
     * keeps both in one tag, and a disc number likewise; each field shows its own half.
     */
    private fun tagsOf(properties: PropertyMap): TrackTags {
        fun first(field: TagField) = properties[field.key]?.firstOrNull()?.trim().orEmpty()
        val track = first(TagField.TRACK_NUMBER)
        return TagField.entries.fold(TrackTags()) { tags, field ->
            val value = when (field) {
                TagField.TRACK_NUMBER -> track.substringBefore('/')
                TagField.TRACK_TOTAL -> first(field).ifEmpty { track.substringAfter('/', missingDelimiterValue = "") }
                TagField.DISC_NUMBER -> first(field).substringBefore('/')
                else -> first(field)
            }
            tags.with(field, value.trim())
        }
    }

    /**
     * Sets [field] to its value in [tags]. The track count goes where the file already keeps it: a tag of
     * its own, or after the number in one - the form every format reads. A disc number keeps the count of
     * discs it had.
     */
    private fun PropertyMap.write(field: TagField, tags: TrackTags) {
        when (field) {
            TagField.TRACK_NUMBER, TagField.TRACK_TOTAL -> {
                val number = tags[TagField.TRACK_NUMBER]
                val total = tags[TagField.TRACK_TOTAL]
                if (containsKey(TagField.TRACK_TOTAL.key)) {
                    set(TagField.TRACK_NUMBER.key, number)
                    set(TagField.TRACK_TOTAL.key, total)
                } else {
                    set(TagField.TRACK_NUMBER.key, if (number.isBlank() || total.isBlank()) number else "$number/$total")
                }
            }
            TagField.DISC_NUMBER -> {
                val discCount = this[field.key]?.firstOrNull()?.substringAfter('/', missingDelimiterValue = "").orEmpty()
                val number = tags[field]
                set(field.key, if (number.isBlank() || discCount.isBlank()) number else "$number/$discCount")
            }
            else -> set(field.key, tags[field])
        }
    }

    /** [key] set to [value] alone, or taken out where [value] is blank. */
    private fun PropertyMap.set(key: String, value: String) {
        if (value.isBlank()) remove(key) else put(key, arrayOf(value))
    }

    /** What [bytes] are, told by their signature: a PNG, or - as every catalogue's cover and every picture from the device is - a JPEG. */
    private fun imageMimeTypeOf(bytes: ByteArray): String =
        if (bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
}
