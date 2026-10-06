package com.lhacenmed.sona.feature.video.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

private const val Timeout = 300L

class TapSequenceTest {

    private val taps = TapSequence(Timeout)

    /** A tap going down at [atMs] and lifting 50 ms later. */
    private fun tap(atMs: Long, zone: TapZone, answersDoubleTap: Boolean = true) =
        taps.onTap(downMs = atMs, upMs = atMs + 50, zone = zone, answersDoubleTap = answersDoubleTap)

    @Test
    fun `the first tap ever is a tap of its own, however early it comes`() {
        assertEquals(TapOutcome.Single, tap(0, TapZone.END))
        assertEquals(TapOutcome.Single, TapSequence(Timeout).onTap(Long.MIN_VALUE, Long.MIN_VALUE + 50, TapZone.START, true))
    }

    @Test
    fun `slow taps are each a tap of their own`() {
        assertEquals(TapOutcome.Single, tap(1_000, TapZone.CENTER))
        assertEquals(TapOutcome.Single, tap(2_000, TapZone.CENTER))
    }

    @Test
    fun `a quick second tap undoes the first and double taps`() {
        tap(1_000, TapZone.END)
        assertEquals(TapOutcome.Double(TapZone.END, undoesTap = true), tap(1_200, TapZone.END))
    }

    @Test
    fun `quick taps after a side double tap keep seeking without undoing anything`() {
        tap(1_000, TapZone.START)
        tap(1_200, TapZone.START)
        assertEquals(TapOutcome.Double(TapZone.START, undoesTap = false), tap(1_400, TapZone.START))
        assertEquals(TapOutcome.Double(TapZone.END, undoesTap = false), tap(1_600, TapZone.END))
    }

    @Test
    fun `a middle double tap stands alone`() {
        tap(1_000, TapZone.CENTER)
        assertEquals(TapOutcome.Double(TapZone.CENTER, undoesTap = true), tap(1_200, TapZone.CENTER))
        assertEquals(TapOutcome.Single, tap(1_400, TapZone.CENTER))
    }

    @Test
    fun `a middle tap ends a seeking series`() {
        tap(1_000, TapZone.END)
        tap(1_200, TapZone.END)
        assertEquals(TapOutcome.Single, tap(1_400, TapZone.CENTER))
    }

    @Test
    fun `a pause ends a seeking series`() {
        tap(1_000, TapZone.END)
        tap(1_200, TapZone.END)
        assertEquals(TapOutcome.Single, tap(2_000, TapZone.END))
    }

    @Test
    fun `without double taps every tap is its own`() {
        tap(1_000, TapZone.END, answersDoubleTap = false)
        assertEquals(TapOutcome.Single, tap(1_100, TapZone.END, answersDoubleTap = false))
    }

    @Test
    fun `a reset ends whatever taps were under way`() {
        tap(1_000, TapZone.END)
        taps.reset()
        assertEquals(TapOutcome.Single, tap(1_100, TapZone.END))
    }

    @Test
    fun `the zones are the screen's thirds`() {
        assertEquals(TapZone.START, TapZone.at(10f, 300f))
        assertEquals(TapZone.CENTER, TapZone.at(150f, 300f))
        assertEquals(TapZone.END, TapZone.at(290f, 300f))
    }
}
