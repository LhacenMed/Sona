package com.lhacenmed.sona.feature.update.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private const val BOLD  = "**"
private const val BULLET = "- "

/**
 * Release notes as the release pipeline writes them: markdown, already in the reader's language - in the
 * update sheet and the changelog alike.
 *
 * Only the marks release notes actually use are understood — a heading, a bullet, a run of bold —
 * because the alternative is carrying a markdown library to honour four rules. Anything else is
 * shown as it was written, which reads as prose rather than as a mistake.
 */
@Composable
fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        markdown.lines().forEach { line ->
            val text = line.trim()
            when {
                text.isEmpty() -> Spacer(Modifier.height(6.dp))

                // Any depth of heading reads at one weight: these are a few lines on a dialog,
                // not a document with an outline to convey.
                text.startsWith("#") -> Text(
                    text     = boldRuns(text.trimStart('#').trim()),
                    style    = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 2.dp),
                )

                text.startsWith(BULLET) -> Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text     = boldRuns(text.removePrefix(BULLET)),
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                else -> Text(text = boldRuns(text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The `**bold**` runs in [text] set in bold; the rest, and an unclosed run, left as written. */
private fun boldRuns(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (true) {
        val open = text.indexOf(BOLD, cursor)
        val close = if (open < 0) -1 else text.indexOf(BOLD, open + BOLD.length)
        if (close < 0) {
            append(text.substring(cursor))
            return@buildAnnotatedString
        }
        append(text.substring(cursor, open))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(text.substring(open + BOLD.length, close))
        }
        cursor = close + BOLD.length
    }
}
