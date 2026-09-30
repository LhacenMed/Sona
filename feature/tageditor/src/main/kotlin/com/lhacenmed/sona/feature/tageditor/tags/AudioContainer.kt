package com.lhacenmed.sona.feature.tageditor.tags

/**
 * What an audio or video file really is, as TagLib has to be told: TagLib picks the tags it reads and
 * writes by a file's extension before it looks inside, so a file named for one container and holding
 * another - an MP4 saved as `.mp3` - would be read as the one and written as it, the tags landing where no
 * player reads them. [extension] is the name TagLib reads each by.
 */
internal enum class AudioContainer(val extension: String) {
    MPEG("mp3"),
    MP4("m4a"),
    FLAC("flac"),
    OGG("ogg"),
    WAV("wav"),
    AIFF("aiff"),
    MATROSKA("mka"),
    ASF("wma"),
    APE("ape"),
    WAVPACK("wv"),
    DSF("dsf"),
    DSDIFF("dff"),
    ;

    companion object {
        /** The container TagLib takes a file named with [extension] to be, or null where it looks inside instead. */
        fun named(extension: String): AudioContainer? = when (extension.lowercase()) {
            "mp3", "mp2", "aac" -> MPEG
            "m4a", "m4r", "m4b", "m4p", "mp4", "3g2", "m4v" -> MP4
            "flac" -> FLAC
            "ogg", "oga", "opus" -> OGG
            "wav" -> WAV
            "aif", "aiff", "afc", "aifc" -> AIFF
            "mka", "mkv", "webm" -> MATROSKA
            "wma", "asf" -> ASF
            "ape" -> APE
            "wv" -> WAVPACK
            "dsf" -> DSF
            "dff", "dsdiff" -> DSDIFF
            else -> null
        }

        /**
         * The container [head] - a file's first bytes, past any ID3v2 tag in front of them - opens, or null
         * where they say none TagLib tags. MPEG audio has no mark of its own, so it is what is left when
         * [hadId3] says an ID3v2 tag stood in front.
         */
        fun of(head: ByteArray, hadId3: Boolean): AudioContainer? = when {
            head.hasAt(4, "ftyp") -> MP4
            head.hasAt(0, "fLaC") -> FLAC
            head.hasAt(0, "OggS") -> OGG
            head.hasAt(0, "RIFF") && head.hasAt(8, "WAVE") -> WAV
            head.hasAt(0, "FORM") && (head.hasAt(8, "AIFF") || head.hasAt(8, "AIFC")) -> AIFF
            head.hasAt(0, byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())) -> MATROSKA
            head.hasAt(0, byteArrayOf(0x30, 0x26, 0xB2.toByte(), 0x75, 0x8E.toByte(), 0x66, 0xCF.toByte(), 0x11)) -> ASF
            head.hasAt(0, "MAC ") -> APE
            head.hasAt(0, "wvpk") -> WAVPACK
            head.hasAt(0, "DSD ") -> DSF
            head.hasAt(0, "FRM8") -> DSDIFF
            hadId3 || head.isMpegFrameSync() -> MPEG
            else -> null
        }

        private fun ByteArray.hasAt(offset: Int, text: String): Boolean = hasAt(offset, text.toByteArray(Charsets.ISO_8859_1))

        private fun ByteArray.hasAt(offset: Int, bytes: ByteArray): Boolean =
            size >= offset + bytes.size && bytes.indices.all { this[offset + it] == bytes[it] }

        /** An MPEG audio frame - or an AAC one in ADTS - starts on eleven set bits. */
        private fun ByteArray.isMpegFrameSync(): Boolean =
            size >= 2 && this[0] == 0xFF.toByte() && (this[1].toInt() and 0xE0) == 0xE0
    }
}
