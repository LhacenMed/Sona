package com.lhacenmed.sona.core.designsystem.component.screen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import com.lhacenmed.sona.core.designsystem.component.section.SectionListState
import com.lhacenmed.sona.core.designsystem.component.section.section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LazyListKeyIndexTest {

    private fun content(state: SectionListState): LazyListScope.() -> Unit = {
        section(key = "albums", title = "Albums", state = state) {
            items(listOf("a", "b"), key = { "album-$it" }) {}
        }
        // Divider, then header, then the tracks.
        section(key = "tracks", title = "Tracks", state = state, hasDividerAbove = true) {
            items(listOf(10L, 20L, 30L), key = { it }) {}
        }
    }

    @Test
    fun findsARowAfterEveryHeadingAndDividerBeforeIt() {
        val state = SectionListState(emptySet())
        // albums header 0, a 1, b 2, divider 3, tracks header 4, 10 at 5, 20 at 6
        assertEquals(6, content(state).indexOfKey(20L))
        assertEquals(1, content(state).indexOfKey("album-a"))
    }

    @Test
    fun aFoldedSectionLaysOutNoRowsToFind() {
        val folded = SectionListState(setOf("albums"))
        assertEquals(4, content(folded).indexOfKey(20L))
        assertNull(content(folded).indexOfKey("album-a"))
    }
}
