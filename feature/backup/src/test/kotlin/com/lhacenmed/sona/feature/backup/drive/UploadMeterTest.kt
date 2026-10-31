package com.lhacenmed.sona.feature.backup.drive

import com.lhacenmed.sona.feature.backup.BackupProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadMeterTest {

    private var last: BackupProgress? = null

    private fun meter() = UploadMeter(filesDone = 2, filesTotal = 4, bytesDone = 200, bytesTotal = 1_000) { last = it }

    @Test
    fun countsFrom_whatDriveAlreadyHas() {
        meter().report()
        assertEquals(2, last!!.done)
        assertEquals(200L, last!!.transfer!!.done)
        assertEquals(0.2f, last!!.fraction!!, 0.001f)
    }

    @Test
    fun aFileResumedPartWay_countsItsBytesAsDone_butNotAsSent() {
        val meter = meter()
        meter.moved("a", 300)
        meter.moved("a", 350)
        assertEquals(550L, last!!.transfer!!.done)
        assertEquals(50L, last!!.transfer!!.sent)
    }

    @Test
    fun bytesSentAgain_afterADroppedConnection_countAsSentTwice() {
        val meter = meter()
        meter.moved("a", 0)
        meter.moved("a", 100)
        meter.moved("a", 40) // Drive had only 40 of them.
        meter.moved("a", 100)
        assertEquals(300L, last!!.transfer!!.done)
        assertEquals(160L, last!!.transfer!!.sent)
    }

    @Test
    fun aFinishedFile_countsWhole_andAFailedOne_notAtAll() {
        val meter = meter()
        meter.moved("a", 0)
        meter.moved("a", 90)
        meter.finished("a", 100)
        meter.moved("b", 0)
        meter.moved("b", 50)
        meter.failed("b")
        assertEquals(3, last!!.done)
        assertEquals(300L, last!!.transfer!!.done)
        assertEquals(140L, last!!.transfer!!.sent)
    }
}
