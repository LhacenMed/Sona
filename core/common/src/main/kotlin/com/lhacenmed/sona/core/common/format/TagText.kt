package com.lhacenmed.sona.core.common.format

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** The single-byte encodings a tag's UTF-8 is read as by mistake: Latin-1, and Windows' superset of it. */
private val MisreadEncodings = listOf(Charsets.ISO_8859_1, Charset.forName("windows-1252"))

/**
 * A tag's text as it was written, where its UTF-8 was read one byte a letter - "SeÃ±orita" for "Señorita".
 *
 * An ID3 tag says which encoding its text is in, and many a tagger writes UTF-8 while saying Latin-1, which
 * every reader then takes it at. Read back to the bytes it was decoded from, such text is UTF-8 through and
 * through - something Latin-1 text written as it says never is but by the rarest chance, its accented
 * letters standing alone where UTF-8 needs them in runs. So text that turns back into UTF-8 is that UTF-8;
 * any other - plain ASCII, real Latin-1, any script beyond it - is left as it is.
 *
 * Every tag Sona reads passes through here, whoever read it: the library's scan and the tag editor alike.
 */
fun String.repairedTagText(): String {
    if (all { it.code < 0x80 }) return this
    for (encoding in MisreadEncodings) {
        if (!encoding.newEncoder().canEncode(this)) continue
        return decodedAsUtf8(toByteArray(encoding)) ?: continue
    }
    return this
}

/** [bytes] as UTF-8, or null where they are not UTF-8 at all. */
private fun decodedAsUtf8(bytes: ByteArray): String? = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    null
}
