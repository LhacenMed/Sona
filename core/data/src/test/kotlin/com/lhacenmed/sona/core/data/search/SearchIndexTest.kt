package com.lhacenmed.sona.core.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {

    private val titles = listOf("Feet Don't Fail Me Now", "Mr. Feel", "Seven", "Beyoncé", "Therefore I Am", "أعلى سالم")

    private val index = SearchIndex(titles) { listOf(it) }

    private fun search(query: String) = index.search(query).items

    @Test
    fun findsMrFeelHoweverItIsTyped() {
        listOf("Mr. Feel", "mr feel", "MR-FEEL", "mrfeel", "rfee", "mrfl", "mf", "feel mr", "mr feal", "mr fel").forEach { query ->
            assertTrue("\"$query\" should find Mr. Feel", "Mr. Feel" in search(query))
        }
    }

    @Test
    fun ranksTheExactMatchFirst() {
        assertEquals("Mr. Feel", search("mr feel").first())
        assertEquals("Seven", search("seven").first())
    }

    @Test
    fun readsAccentsApostrophesAndArabicMarksAsTheBareLetters() {
        assertEquals(listOf("Beyoncé"), search("beyonce"))
        assertEquals("Feet Don't Fail Me Now", search("dont fail").first())
        assertEquals(listOf("أعلى سالم"), search("اعلي"))
    }

    @Test
    fun aShortWordIsNeverFoundByATypo() {
        assertFalse("Seven" in search("am"))
    }

    @Test
    fun findsEverythingForNothingToCompare() {
        assertEquals(titles, search("  . "))
    }

    @Test
    fun putsWhatIsFoundOtherwiseLast() {
        val result = index.search("seven") { it == "Therefore I Am" }
        assertEquals(listOf("Seven", "Therefore I Am"), result.items)
        assertEquals(listOf("Therefore I Am"), result.foundOtherwise)
    }
}
