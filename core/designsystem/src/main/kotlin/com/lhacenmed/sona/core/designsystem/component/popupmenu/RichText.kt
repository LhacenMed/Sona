package com.lhacenmed.sona.core.designsystem.component.popupmenu

/**
 * A sequence of [TextSegment]s composing a single paragraph of mixed plain + link text.
 * Pass to [MenuItem.richText] to render a [LinkTextView] instead of a plain [TextView].
 *
 * Build via the [buildRichText] DSL:
 * ```
 * buildRichText {
 *     plain("See the ")
 *     link("privacy policy", color = 0xFF4CAF50.toInt()) { openPrivacyPolicy() }
 *     plain(" for details.")
 * }
 * ```
 */
class RichText(val segments: List<TextSegment>)

/** A single run of text inside a [RichText]. */
sealed class TextSegment {
    /** Non-interactive text rendered in the default content color. */
    data class Plain(val text: String) : TextSegment()

    /**
     * Tappable link rendered in [color]. On press, a per-line rounded rectangle is drawn
     * behind the span — identical to Telegram's self-destruct timer link style.
     * [onClick] fires when the finger lifts over the span.
     */
    data class Link(val text: String, val color: Int, val onClick: () -> Unit) : TextSegment()
}

/** DSL entry-point — builds a [RichText] from a lambda. */
fun buildRichText(block: RichTextBuilder.() -> Unit): RichText =
    RichTextBuilder().apply(block).build()

/** Builder for [RichText]. Use [plain] and [link] to compose text runs. */
class RichTextBuilder {
    private val segments = mutableListOf<TextSegment>()

    fun plain(text: String) {
        segments += TextSegment.Plain(text)
    }

    fun link(text: String, color: Int, onClick: () -> Unit) {
        segments += TextSegment.Link(text, color, onClick)
    }

    internal fun build(): RichText = RichText(segments)
}
