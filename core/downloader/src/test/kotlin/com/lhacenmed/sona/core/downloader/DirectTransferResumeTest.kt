package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.downloader.DirectTransfer.PartRecord
import com.lhacenmed.sona.core.downloader.transfer.ContentRange
import com.lhacenmed.sona.core.downloader.transfer.FilePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectTransferResumeTest {

    @Test
    fun `a part is carried on by its range of a file of its very size`() {
        assertTrue(continuesPart(code = 206, range = ContentRange(start = 1_000, total = 5_000), offset = 1_000, heldTotal = 5_000))
    }

    @Test
    fun `a range of a file of another size is another file`() {
        assertFalse(continuesPart(code = 206, range = ContentRange(start = 1_000, total = 6_000), offset = 1_000, heldTotal = 5_000))
    }

    @Test
    fun `a range from elsewhere does not carry the part on`() {
        assertFalse(continuesPart(code = 206, range = ContentRange(start = 0, total = 5_000), offset = 1_000, heldTotal = 5_000))
    }

    @Test
    fun `the whole sent again never carries a part on - not even the first`() {
        assertFalse(continuesPart(code = 200, range = null, offset = 0, heldTotal = 5_000))
    }

    @Test
    fun `a record reads back as it was written, every part where it was`() {
        val record = PartRecord("251", 4_200_000, "\"abc\"", listOf(FilePart(0, 2_099_999, 1_000), FilePart(2_100_000, 4_199_999, 4_200_000)))
        assertEquals(record.toJson(), PartRecord.parse(record.toJson())?.toJson())
        val unvalidated = record.copy(validator = null)
        assertEquals(unvalidated.toJson(), PartRecord.parse(unvalidated.toJson())?.toJson())
    }

    @Test
    fun `a record cut off as it was written reads as none`() {
        assertNull(PartRecord.parse("""{"formatId":"251","tot"""))
    }

    @Test
    fun `a record from before parts reads as none, so its download starts over`() {
        assertNull(PartRecord.parse("""{"formatId":"251","totalBytes":4200000,"validator":"x"}"""))
    }
}
