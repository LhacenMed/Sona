package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.downloader.DirectTransfer.PartRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectTransferResumeTest {

    @Test
    fun `a part is carried on by a range of a file of its very size`() {
        assertTrue(continuesPart(code = 206, offset = 1_000, heldTotal = 5_000, sentTotal = 5_000))
    }

    @Test
    fun `a range of a file of another size starts the part again`() {
        assertFalse(continuesPart(code = 206, offset = 1_000, heldTotal = 5_000, sentTotal = 6_000))
    }

    @Test
    fun `the whole sent again starts the part again`() {
        assertFalse(continuesPart(code = 200, offset = 1_000, heldTotal = 5_000, sentTotal = 5_000))
    }

    @Test
    fun `a part of no known size is never carried on`() {
        assertFalse(continuesPart(code = 206, offset = 1_000, heldTotal = null, sentTotal = 5_000))
    }

    @Test
    fun `nothing held is carried on by anything`() {
        assertTrue(continuesPart(code = 200, offset = 0, heldTotal = null, sentTotal = null))
    }

    @Test
    fun `a record reads back as it was written`() {
        val record = PartRecord("251", 4_200_000, "\"abc\"")
        assertEquals(record, PartRecord.parse(record.toJson()))
        assertEquals(record.copy(validator = null), PartRecord.parse(record.copy(validator = null).toJson()))
    }

    @Test
    fun `a record cut off as it was written reads as none`() {
        assertNull(PartRecord.parse("""{"formatId":"251","tot"""))
    }
}
