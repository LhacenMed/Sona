package com.lhacenmed.sona.feature.video.subtitle

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** The largest file taken for subtitles - far past any film's, and short of a video picked by mistake filling memory. */
private const val MAX_SUBTITLE_FILE_BYTES = 8 * 1024 * 1024

private const val TTML_MIME_TYPE = MimeTypes.APPLICATION_TTML

/** The file types subtitles can be read from, by extension. */
private val MimeTypeOfExtension = mapOf(
    "srt" to MimeTypes.APPLICATION_SUBRIP,
    "ass" to MimeTypes.TEXT_SSA,
    "ssa" to MimeTypes.TEXT_SSA,
    "vtt" to MimeTypes.TEXT_VTT,
    "ttml" to TTML_MIME_TYPE,
    "dfxp" to TTML_MIME_TYPE,
)

private fun isSubtitleExtension(extension: String): Boolean = extension.lowercase(Locale.ROOT) in MimeTypeOfExtension

/** All of this stream, or null where it runs past what any subtitle file does. */
internal fun InputStream.readSubtitleBytes(): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) return output.toByteArray()
        output.write(buffer, 0, read)
        if (output.size() > MAX_SUBTITLE_FILE_BYTES) return null
    }
}

/** A subtitle file found beside a video: its [fileName], and the [language] named in it, if any. */
internal data class SideSubtitle(val fileName: String, val language: String?)

/**
 * Which of [siblingNames] are subtitles of the video called [videoFileName] - its name with a subtitle's extension,
 * as `Movie.srt` is to `Movie.mkv`, and perhaps a language between them, as in `Movie.ar.srt`.
 */
internal fun sideSubtitlesOf(videoFileName: String, siblingNames: List<String>): List<SideSubtitle> {
    val videoBase = videoFileName.substringBeforeLast('.')
    return siblingNames.mapNotNull { name ->
        val extension = name.substringAfterLast('.', "")
        if (!isSubtitleExtension(extension)) return@mapNotNull null
        val base = name.substringBeforeLast('.')
        when {
            base.equals(videoBase, ignoreCase = true) -> SideSubtitle(name, null)
            base.length > videoBase.length + 1 &&
                base.startsWith(videoBase, ignoreCase = true) &&
                base[videoBase.length] == '.' -> SideSubtitle(name, languageTagIn(base.substring(videoBase.length + 1)))
            else -> null
        }
    }.sortedBy { it.fileName.lowercase(Locale.ROOT) }
}

/** The first of the dot-separated [tags] that reads as a language code - "ar" of "ar.forced". */
private fun languageTagIn(tags: String): String? =
    tags.split('.').firstOrNull { LanguageTag.matches(it) }

private val LanguageTag = Regex("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,4})?")

/**
 * [bytes] as UTF-8, what every subtitle parser reads, whatever they were written in: their own marker says so, or they
 * already are, or [detectCharset] names what they look like - older subtitles are mostly in a regional encoding.
 */
internal fun subtitleBytesAsUtf8(bytes: ByteArray, detectCharset: (ByteArray) -> String?): ByteArray {
    val marked = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> return bytes.copyOfRange(3, bytes.size)
        bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE
        bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE
        else -> null
    }
    if (marked != null) return String(bytes, 2, bytes.size - 2, marked).toByteArray(Charsets.UTF_8)
    if (decodesCleanly(bytes, Charsets.UTF_8)) return bytes
    val detected = detectCharset(bytes)?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
    return String(bytes, detected ?: Charsets.ISO_8859_1).toByteArray(Charsets.UTF_8)
}

/** The regional encodings of alphabets written in whole words of non-ASCII bytes, each with its commonest letters. */
private val AlphabetEncodings = mapOf(
    "windows-1256" to "\u0627\u0644\u0645\u064A\u0646\u0648\u0631\u062A\u0628\u0639",
    "windows-1251" to "\u043E\u0435\u0430\u0438\u043D\u0442\u0441\u0440\u0432\u043B",
    "windows-1255" to "\u05D9\u05D5\u05D4\u05DC\u05D0\u05E8\u05EA\u05DD\u05D1\u05DE",
    "windows-1253" to "\u03B1\u03BF\u03B9\u03B5\u03C4\u03BD\u03C3\u03C1\u03B7\u03C0",
)

/** The encodings of languages a file's bytes alone cannot tell, by the language of the device that is likely reading them. */
private val EastAsianEncodingOfLanguage = mapOf("zh" to "GBK", "ja" to "Shift_JIS", "ko" to "EUC-KR")
private val LatinEncodingOfLanguage = mapOf(
    "tr" to "windows-1254",
    "pl" to "windows-1250",
    "cs" to "windows-1250",
    "sk" to "windows-1250",
    "hu" to "windows-1250",
    "ro" to "windows-1250",
    "hr" to "windows-1250",
    "sl" to "windows-1250",
)

/**
 * What [bytes], which are not UTF-8, are most likely written in, on a device in [deviceLanguage]. No file says, and no
 * encoding can be proved, so this goes by what such text looks like:
 * - an accented Latin letter stands alone among plain ones, where the letters of other alphabets come in whole words;
 * - among those alphabets, it is the one its commonest letters are most of the text in.
 */
internal fun detectSubtitleCharset(bytes: ByteArray, deviceLanguage: String): String {
    val highBytes = bytes.indices.filter { bytes[it] < 0 }
    val inWords = highBytes.count { index -> bytes.getOrNull(index - 1).isHigh() || bytes.getOrNull(index + 1).isHigh() }
    if (inWords * 2 < highBytes.size) return LatinEncodingOfLanguage[deviceLanguage] ?: "windows-1252"
    EastAsianEncodingOfLanguage[deviceLanguage]?.takeIf { decodesCleanly(bytes, Charset.forName(it)) }?.let { return it }
    return AlphabetEncodings.maxBy { (encoding, commonLetters) ->
        String(bytes, Charset.forName(encoding)).count { it in commonLetters }
    }.key
}

private fun Byte?.isHigh(): Boolean = this != null && this < 0

private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

private fun decodesCleanly(bytes: ByteArray, charset: Charset): Boolean = try {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
    true
} catch (_: CharacterCodingException) {
    false
}

/**
 * The lines of a file of [extension] holding [bytes] - null where it cannot be read for subtitles, or holds none.
 *
 * A TTML file is left as it is: it says its own encoding, which turning it into UTF-8 would make untrue.
 */
@OptIn(UnstableApi::class)
internal fun parseSubtitle(bytes: ByteArray, extension: String): SubtitleTimeline<Cue>? {
    val mimeType = MimeTypeOfExtension[extension.lowercase(Locale.ROOT)] ?: return null
    val text = if (mimeType == TTML_MIME_TYPE) bytes else subtitleBytesAsUtf8(bytes) { detectSubtitleCharset(it, Locale.getDefault().language) }
    val format = Format.Builder().setSampleMimeType(mimeType).build()
    val stretches = ArrayList<TimedCues<Cue>>()
    val isParsed = runCatching {
        DefaultSubtitleParserFactory().create(format).parse(text, SubtitleParser.OutputOptions.allCues()) { timed ->
            stretches += TimedCues(timed.startTimeUs, timed.endTimeUs.takeUnless { it == C.TIME_UNSET }, timed.cues)
        }
    }.isSuccess
    return if (isParsed && stretches.any { it.cues.isNotEmpty() }) SubtitleTimeline(stretches) else null
}
