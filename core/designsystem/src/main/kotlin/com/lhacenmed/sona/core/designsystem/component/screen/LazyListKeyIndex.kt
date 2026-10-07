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
