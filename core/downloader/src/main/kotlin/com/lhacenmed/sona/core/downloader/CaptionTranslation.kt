package com.lhacenmed.sona.core.downloader

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** How a caption translated by Sona is picked: "fr@en", French translated from the captions "en" names. */
private const val TranslatedFrom = '@'

/** The most text one translation request carries - well within what the endpoint takes. */
private const val MaxBatchChars = 4_000

/** A line of captions: from [startMs] to [endMs], saying [text]. */
internal data class CaptionLine(val startMs: Long, val endMs: Long, val text: String)

/** The code that picks [language] translated from the captions [from] names - see [CaptionTranslation]. */
fun translatedCaptionCode(language: String, from: String): String = "$language$TranslatedFrom$from"

/** The language a picked caption [code] is in - its own, or the one it is translated into. */
fun captionLanguageOf(code: String): String = code.substringBefore(TranslatedFrom)

/**
 * Captions translated by Sona into another language, from the video's own - YouTube refuses its own translations to every
 * reader now. Picked as [translatedCaptionCode]; made before yt-dlp downloads, from the read it is handed: each source's text
 * fetched, translated by Google Translate's free endpoint - a whole file in a request or two - and written as a caption file
 * the read then lists under its language, so yt-dlp embeds it as it embeds any other.
 */
internal object CaptionTranslation {
    private val client by lazy { OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build() }

    /**
     * [picked], with each translation among them made into [folder] and listed in [read] under its language - its code then
     * that language's. A translation that cannot be made, or one with no read to list it in, is left out.
     */
    suspend fun make(read: JSONObject?, picked: List<String>, folder: File): List<String> = withContext(Dispatchers.IO) {
        picked.mapNotNull { code ->
            if (TranslatedFrom !in code) return@mapNotNull code
            val language = captionLanguageOf(code)
            val from = code.substringAfter(TranslatedFrom)
            val lines = read?.let { runCatching { translate(it, from, language) }.getOrNull() } ?: return@mapNotNull null
            read.listMadeCaptions(language, lines, folder)
        }
    }

    /** The captions [from] names in [read], translated into [language] - null where it has none. */
    private fun translate(read: JSONObject, from: String, language: String): List<CaptionLine>? {
        val formats = (read.optJSONObject("subtitles")?.optJSONArray(from) ?: read.optJSONObject("automatic_captions")?.optJSONArray(from)) ?: return null
        val url = formats.objects().firstNotNullOfOrNull { it.stringOrNull("url") } ?: return null
        val lines = linesOf(url)?.takeIf { it.isNotEmpty() } ?: return null
        return lines.chunkedByLength().flatMap { batch -> translated(batch, language) ?: return null }
    }

    /**
     * The lines of the captions at [url] - YouTube's asked for in its own first form, sentence by sentence, which its
     * speech's captions in WebVTT roll over line by line.
     */
    private fun linesOf(url: String): List<CaptionLine>? {
        val youTube = url.toHttpUrlOrNull()?.takeIf { it.encodedPath == "/api/timedtext" }
        val address = youTube?.newBuilder()?.removeAllQueryParameters("fmt")?.build()?.toString() ?: url
        val text = client.newCall(Request.Builder().url(address).build()).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        return if (youTube != null) sentencesOf(text) else vttLinesOf(text)
    }

    /** [lines] translated into [language] - one request for them all, each line kept apart; null where the endpoint fails. */
    private fun translated(lines: List<CaptionLine>, language: String): List<CaptionLine>? {
        val request = Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t&tl=$language")
            .post(FormBody.Builder().add("q", lines.joinToString("\n") { it.text }).build())
            .build()
        val answer = client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        val texts = JSONArray(answer).optJSONArray(0)?.let { parts -> (0 until parts.length()).joinToString("") { parts.optJSONArray(it)?.optString(0).orEmpty() } }
            ?.split('\n')?.map(String::trim) ?: return null
        // A line the endpoint ran into the next is asked for again, alone.
        if (texts.size != lines.size) return lines.map { line -> if (lines.size == 1) return null else translated(listOf(line), language)?.single() ?: return null }
        return lines.zip(texts) { line, text -> line.copy(text = text) }
    }

    private fun List<CaptionLine>.chunkedByLength(): List<List<CaptionLine>> = fold(mutableListOf(mutableListOf<CaptionLine>())) { batches, line ->
        if (batches.last().sumOf { it.text.length + 1 } + line.text.length > MaxBatchChars) batches += mutableListOf<CaptionLine>()
        batches.last() += line
        batches
    }
}

/**
 * [lines], captions Sona made in [language], written into [folder] as SubRip and listed in this read as a file of its own - so
 * yt-dlp embeds or saves them as any other. Listed under [language], or beside the video's own in it. The code that picks them.
 */
internal fun JSONObject.listMadeCaptions(language: String, lines: List<CaptionLine>, folder: File): String {
    val subtitles = optJSONObject("subtitles") ?: JSONObject().also { put("subtitles", it) }
    val taken = subtitles.has(language) || optJSONObject("automatic_captions")?.has(language) == true
    val code = if (taken) "$language-sona" else language
    val file = File(folder.apply { mkdirs() }, "${optString("id")}.$code.srt").apply { writeText(srtOf(lines)) }
    subtitles.put(code, JSONArray().put(JSONObject().put("ext", "srt").put("url", file.toURI().toString())))
    return code
}

/** YouTube's captions as sentences: `<text start="1.5" dur="2.1">…</text>`. */
internal fun sentencesOf(xml: String): List<CaptionLine> =
    Regex("""<text start="([\d.]+)"(?: dur="([\d.]+)")?[^>]*>(.*?)</text>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { match ->
        val start = (match.groupValues[1].toDouble() * 1000).toLong()
        val length = ((match.groupValues[2].toDoubleOrNull() ?: 0.0) * 1000).toLong()
        val text = unescaped(match.groupValues[3]).replace('\n', ' ').trim()
        CaptionLine(start, start + length, text).takeIf { text.isNotEmpty() }
    }.toList()

/** WebVTT's lines: each cue's times and its text, its tags left out. */
internal fun vttLinesOf(vtt: String): List<CaptionLine> =
    vtt.replace("\r\n", "\n").split(Regex("\n{2,}")).mapNotNull { block ->
        val lines = block.lines()
        val timing = lines.indexOfFirst { "-->" in it }.takeIf { it >= 0 } ?: return@mapNotNull null
        val (start, end) = lines[timing].split("-->").map { millisOf(it.trim().substringBefore(' ')) ?: return@mapNotNull null }
        val text = lines.drop(timing + 1).joinToString(" ") { unescaped(it.replace(Regex("<[^>]+>"), "")).trim() }.trim()
        CaptionLine(start, end, text).takeIf { text.isNotEmpty() }
    }

/** "01:02:03.456", or "02:03.456", in milliseconds. */
private fun millisOf(time: String): Long? {
    val parts = time.replace(',', '.').split(':')
    val seconds = parts.lastOrNull()?.toDoubleOrNull() ?: return null
    return (parts.dropLast(1).fold(0L) { total, part -> total * 60 + (part.toLongOrNull() ?: return null) } * 60_000) + (seconds * 1000).toLong()
}

private fun unescaped(text: String): String =
    text.replace("&amp;#39;", "'").replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

/** [lines] as SubRip. */
internal fun srtOf(lines: List<CaptionLine>): String = lines.mapIndexed { index, line ->
    "${index + 1}\n${srtTime(line.startMs)} --> ${srtTime(line.endMs)}\n${line.text}\n"
}.joinToString("\n")

private fun srtTime(ms: Long) = "%02d:%02d:%02d,%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
