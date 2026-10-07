package com.lhacenmed.sona.core.data.sort

import com.lhacenmed.sona.core.model.sort.SortDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrangementTest {

    private data class Item(val name: String, val position: Int?, val addedAt: Long)

    private val arrangement = Arrangement<Item>(position = { it.position }, addedAt = { it.addedAt })

    // Arranged c, a, b; then d and e added later, e last.
    private val items = listOf(
        Item("e", null, 50),
        Item("a", 1, 10),
        Item("d", null, 40),
        Item("b", 2, 20),
        Item("c", 0, 30),
    )

    @Test
    fun newTracksGoToTheBottomOldestFirst() {
        assertEquals(listOf("c", "a", "b", "d", "e"), arrangement.sort(items, SortDirection.ASCENDING).map { it.name })
    }

    @Test
    fun newTracksGoToTheTopNewestFirst() {
        assertEquals(listOf("e", "d", "c", "a", "b"), arrangement.sort(items, SortDirection.DESCENDING).map { it.name })
    }

    @Test
    fun aListNeverArrangedRunsInTheOrderItsTracksJoinedIt() {
        val joined = items.map { it.copy(position = null) }
        assertEquals(listOf("a", "b", "c", "d", "e"), arrangement.sort(joined, SortDirection.ASCENDING).map { it.name })
    }
}
