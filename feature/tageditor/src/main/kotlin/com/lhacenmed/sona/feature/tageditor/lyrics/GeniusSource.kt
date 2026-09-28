package com.lhacenmed.sona.feature.tageditor.lyrics

import android.text.Html
import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.objects
import com.lhacenmed.sona.feature.tageditor.net.string

/**
 * Genius - syncedlyrics' `Genius`: its search, then the song's page, whose lyrics it reads. Plain lyrics only,
 * and no length - but the widest catalogue of words there is, for a song no source has timed.
 *
 * The page is read as syncedlyrics reads it - every `data-lyrics-container` in turn, a blank line before each
 * "[Verse]" - with two differences its own reading gets wrong: the header Genius puts inside the first one
 * (its contributors, its translations) is left out, being marked `data-exclude-from-selection`; and the text is
 * taken as a browser lays it out, so a line with an annotation in it stays one line.
 */
internal object GeniusSource : LyricsSource {
    private const val SearchApi = "https://genius.com/api/search/multi?per_page=5&q="
    private val SearchHeaders = mapOf("cookie" to "obuid=e3ee67e0-7df9-4181-8324-d977c6dc9250")

    /** A desktop browser, which is who the song page is rendered for. */
    private val PageHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    )

    private const val LyricsContainer = "data-lyrics-container=\"true\""
    private const val ExcludedFromLyrics = "data-exclude-from-selection=\"true\""
    private val SectionHeading = Regex("""\n\[""")
    private val ExtraBlankLines = Regex("""\n{3,}""")

    override val name = "Genius"

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> {
        // The "song" section - syncedlyrics' sections[1] - named rather than counted, so a reordering cannot move it.
        val url = Http.json(SearchApi + Http.encode(query.searchTerm), SearchHeaders)
            ?.optJSONObject("response")
            ?.objects("sections")
            ?.firstOrNull { it.string("type") == "song" }
            ?.objects("hits")
            ?.firstOrNull()
            ?.optJSONObject("result")
            ?.string("url")
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        val page = Http.text(url, PageHeaders) ?: return emptyList()
        val lyrics = divsWith(page, LyricsContainer)
            .joinToString("") { container -> textOf(withoutDivsWith(container, ExcludedFromLyrics)) }
            .replace(SectionHeading, "\n\n[")
            .replace(ExtraBlankLines, "\n\n")
            .trim()
        return listOfNotNull(lyrics.takeIf { it.isNotBlank() }?.let { SourceLyrics(it, durationMs = null) })
    }

    /** The inner HTML of every `<div>` whose opening tag carries [attribute]. */
    private fun divsWith(html: String, attribute: String): List<String> =
        divRanges(html, attribute).map { (open, close) -> html.substring(html.indexOf('>', open) + 1, close) }

    /** [html] with every `<div>` whose opening tag carries [attribute] taken out, contents and all. */
    private fun withoutDivsWith(html: String, attribute: String): String {
        var result = html
        for ((open, close) in divRanges(html, attribute).asReversed()) {
            result = result.removeRange(open, close + "</div>".length)
        }
        return result
    }

    /**
     * Where each outermost `<div>` whose opening tag carries [attribute] starts, and where its own `</div>`
     * does - found by counting the divs opened and closed inside it.
     */
    private fun divRanges(html: String, attribute: String): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        var from = 0
        while (true) {
            val marker = html.indexOf(attribute, from).takeIf { it >= 0 } ?: break
            val open = html.lastIndexOf("<div", marker).takeIf { it >= 0 } ?: break
            var depth = 0
            var cursor = open
            var close = -1
            while (cursor < html.length) {
                val nextOpen = html.indexOf("<div", cursor)
                val nextClose = html.indexOf("</div", cursor)
                if (nextClose < 0) break
                if (nextOpen in 0 until nextClose) {
                    depth++
                    cursor = nextOpen + 4
                } else {
                    depth--
                    if (depth == 0) {
                        close = nextClose
                        break
                    }
                    cursor = nextClose + 5
                }
            }
            if (close < 0) break
            ranges += open to close
            from = close
        }
        return ranges
    }

    /** [html] as text, the way a browser lays it out: `<br>` a new line, entities read, tags gone. */
    private fun textOf(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
}
