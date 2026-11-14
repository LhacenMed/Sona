/*
 * Ported from AB Download Manager (https://github.com/amir1376/ab-download-manager) - its
 * `fileNameExtractor.kt`, `FilenameDecoder.kt` and `HttpUrlUtils.extractNameFromLink`.
 * Copyright AmirHossein Abdolmotallebi, licensed under the Apache License, Version 2.0:
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.lhacenmed.sona.core.downloader.transfer

import android.webkit.MimeTypeMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What a file served at [url] is named: as its `Content-Disposition` says, else a `filename` in its link, else its link's last
 * part - AB Download Manager's order. Its extension is left as it is, but for two names that would not open: one with none is
 * given [mimeType]'s, and a song or a video named as anything else - `download.aspx` - is named as the media it is.
 * [mimeType] is the file's own kind, never a generic one.
 */
internal fun fileNameOf(url: String, contentDisposition: String?, mimeType: String?): String? {
    val name = contentDisposition?.let(::fileNameFromContentDisposition) ?: fileNameFromLink(url) ?: return null
    val typeExtension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: return name
    val nameExtension = name.substringAfterLast('.', "").lowercase()
    val nameType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(nameExtension)
    return when {
        nameExtension.isEmpty() -> "$name.$typeExtension"
        mimeType.isMediaType() && nameType?.isMediaType() != true -> "${name.substringBeforeLast('.')}.$typeExtension"
        else -> name
    }
}

private fun String.isMediaType(): Boolean = startsWith("audio/") || startsWith("video/")

/** The file name a `Content-Disposition` value gives - RFC 6266's `filename*`, else its plain `filename` - null where none. */
internal fun fileNameFromContentDisposition(value: String): String? {
    Utf8FileName.find(value)?.groups?.get("fileName")?.value
        ?.let { runCatching { decodeFileName(it) }.getOrNull() }
        ?.let { return it }
    AsciiFileName.find(value)?.groups?.get("fileName")?.value
        ?.let { name -> runCatching { decodeFileName(decodeMimeWords(name)) }.getOrNull() }
        ?.let { return it }
    return null
}

/** The name a link itself gives a file: a `filename` in its query, else its last part - null where it has neither. */
private fun fileNameFromLink(url: String): String? {
    val link = url.toHttpUrlOrNull() ?: return null
    return link.fileNameQuery() ?: link.pathSegments.lastOrNull { it.isNotBlank() }?.let { runCatching { decodeFileName(it) }.getOrNull() }
}

private fun HttpUrl.fileNameQuery(): String? =
    queryParameterNames.firstOrNull { it.equals("filename", ignoreCase = true) }?.let(::queryParameter)?.takeIf { it.isNotBlank() }

private val AsciiFileName = """filename=(["']?)(?<fileName>.*?[^\\])\1(?:; ?|$)""".toRegex(RegexOption.IGNORE_CASE)

private val Utf8FileName = """filename\*=UTF-8''(?<fileName>[^;\s]+)(?:; ?|$)""".toRegex(RegexOption.IGNORE_CASE)

/** Percent-encoding undone as RFC 5987 has it: like a URL decoder, but a `+` stays a `+`. */
private fun decodeFileName(encoded: String): String {
    if ('%' !in encoded) return encoded
    val decoded = StringBuilder()
    val bytes = ByteArray(encoded.length / 3)
    var index = 0
    while (index < encoded.length) {
        if (encoded[index] != '%') {
            decoded.append(encoded[index++])
            continue
        }
        var count = 0
        while (index < encoded.length && encoded[index] == '%') {
            require(index + 2 < encoded.length) { "Incomplete percent encoding at position $index" }
            bytes[count++] = Integer.parseInt(encoded, index + 1, index + 3, 16).toByte()
            index += 3
        }
        decoded.append(String(bytes, 0, count, Charsets.UTF_8))
    }
    return decoded.toString()
}

private val MimeWord = """=\?(?<charset>[^?]+)\?(?<encoding>[BQ])\?(?<encodedText>[^?]+)\?=""".toRegex(RegexOption.IGNORE_CASE)

/** RFC 2047's encoded words - `=?UTF-8?B?…?=` - as mail servers name their attachments, decoded; the rest left as it is. */
@OptIn(ExperimentalEncodingApi::class)
private fun decodeMimeWords(input: String): String = MimeWord.replace(input) { word ->
    runCatching {
        val groups = word.groups
        val text = checkNotNull(groups["encodedText"]).value
        val bytes = when (checkNotNull(groups["encoding"]).value.uppercase()) {
            "B" -> Base64.decode(text)
            else -> quotedPrintable(text)
        }
        String(bytes, charset(checkNotNull(groups["charset"]).value))
    }.getOrNull() ?: word.value
}

/** RFC 2047's Q encoding: `=XX` a byte, `_` a space. */
private fun quotedPrintable(encoded: String): ByteArray {
    val decoded = StringBuilder()
    var index = 0
    while (index < encoded.length) {
        val char = encoded[index]
        val byte = if (char == '=' && index + 2 < encoded.length) encoded.substring(index + 1, index + 3).toIntOrNull(16) else null
        when {
            byte != null -> {
                decoded.append(byte.toChar())
                index += 3
            }
            char == '_' -> {
                decoded.append(' ')
                index++
            }
            else -> {
                decoded.append(char)
                index++
            }
        }
    }
    return decoded.toString().toByteArray(Charsets.ISO_8859_1)
}
