package com.lhacenmed.sona.core.designsystem.component.screen

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable

/**
 * Where the lazy list [content] builds lays out the item keyed [key] - null where it lays out none, as in a
 * section folded away. Read off [content] itself, without laying anything out, so the answer always agrees
 * with the list however its sections, headings and rows are arranged. A state [content] reads - which
 * sections are folded - is read here too, so a derived state built on this follows it.
 */
internal fun (LazyListScope.() -> Unit).indexOfKey(key: Any): Int? = KeyFinder(key).apply(this).foundIndex

/** Counts the items a list's content lays out until one has the key it looks for. */
private class KeyFinder(private val key: Any) : LazyListScope {
    var foundIndex: Int? = null
        private set
    private var itemCount = 0

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        if (foundIndex == null && key == this.key) foundIndex = itemCount
        itemCount++
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        if (foundIndex == null && key != null) {
            for (index in 0 until count) {
                if (key(index) == this.key) {
                    foundIndex = itemCount + index
                    break
                }
            }
        }
        itemCount += count
    }

    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.(Int) -> Unit) {
        if (foundIndex == null && key == this.key) foundIndex = itemCount
        itemCount++
    }
}

/** How many items past the first on screen a section is looked for in: about a screen of rows. */
private const val SectionLookAhead = 32

/**
 * The section of the item [content] lays out at [index], by its key - or, for an item with none of its own, a heading
 * or a row of another kind, of the first one after it that has one, within [SectionLookAhead] items: the section the
 * rows on screen are in. Read off [content] as [indexOfKey] is.
 */
internal fun (LazyListScope.() -> Unit).sectionFrom(index: Int, sectionOf: (key: Any) -> String?): String? =
    SectionFinder(index, sectionOf).apply(this).foundSection

/** Counts the items a list's content lays out, asking each from the one it starts at for its section until one has one. */
private class SectionFinder(private val from: Int, private val sectionOf: (key: Any) -> String?) : LazyListScope {
    var foundSection: String? = null
        private set
    private var itemCount = 0

    private fun look(key: Any?) {
        if (foundSection != null || itemCount < from || itemCount > from + SectionLookAhead || key == null) return
        foundSection = sectionOf(key)
    }

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        look(key)
        itemCount++
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        if (foundSection == null && key != null) {
            val first = (from - itemCount).coerceAtLeast(0)
            val last = (from + SectionLookAhead - itemCount).coerceAtMost(count - 1)
            for (index in first..last) {
                foundSection = sectionOf(key(index))
                if (foundSection != null) break
            }
        }
        itemCount += count
    }

    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.(Int) -> Unit) {
        look(key)
        itemCount++
    }
}
