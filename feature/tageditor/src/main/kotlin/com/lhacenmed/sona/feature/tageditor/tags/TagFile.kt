package com.lhacenmed.sona.feature.tageditor.tags

import android.content.Context
import android.net.Uri
import com.kyant.taglib.AudioPropertiesReadStyle
import com.kyant.taglib.Picture
import com.kyant.taglib.PropertyMap
import com.kyant.taglib.TagLib

/** What the editor reads of a file: its [tags], and its [bitrateKbps] where it can be told. */
data class TagFileInfo(val tags: TrackTags, val bitrateKbps: Long?)

/**
 * The tags inside an audio or video file - read, and written back in place - through TagLib, which tells a
 * format by its content: MP3 and AAC, MP4 and M4A, FLAC, Ogg Vorbis and Opus, Matroska and WebM, WMA, APE,
 * WavPack, WAV, AIFF and DSF among them.
 *
 * TagLib takes every descriptor it is handed as its own and closes it, so each call is given a duplicate.
 * Every call blocks, and is made off the main thread.
 */
internal object TagFile {

    /** The tags the file at [uri] holds, and its bitrate - neither, for a file that cannot be read. */
    fun read(context: Context, uri: Uri): TagFileInfo = runCatching {
        checkNotNull(context.contentResolver.openFileDescriptor(uri, "r")).use { file ->
            val properties = TagLib.getMetadata(file.dup().detachFd(), readPictures = false)?.propertyMap
            val bitrate = TagLib.getAudioProperties(file.dup().detachFd(), AudioPropertiesReadStyle.Fast)?.bitrate
            TagFileInfo(properties?.let(::tagsOf) ?: TrackTags(), bitrate?.toLong()?.takeIf { it > 0 })
        }
    }.getOrDefault(TagFileInfo(TrackTags(), bitrateKbps = null))

    /**
     * Writes [tags] - and [cover], when one was chosen - into the file [target] opens, in place: through a
     * content uri, so a file Android granted that way rather than by its path can be written at all.
     *
     * Only the fields [tags] changes are touched, so whatever else the file holds - a second artist, tags
     * the editor does not show - stays as it was. A field left blank is taken out of the file.
     */
    fun write(context: Context, target: Uri, tags: TrackTags, cover: ByteArray?) {
        checkNotNull(context.contentResolver.openFileDescriptor(target, "rw")) { "Cannot write $target" }.use { file ->
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
