package com.lhacenmed.sona.feature.video.gesture

/** The thirds of the screen a double tap tells apart: back on the start side, play or pause between, on at the end. */
internal enum class TapZone {
    START,
    CENTER,
    END,
    ;

    companion object {
        fun at(x: Float, width: Float): TapZone = when {
            x < width / 3f -> START
            x > width * 2f / 3f -> END
            else -> CENTER
        }
    }
}

/** What one tap on the video turns out to be. */
internal sealed interface TapOutcome {
    /** A tap of its own - shows or hides the controls. */
    data object Single : TapOutcome

    /**
     * The second of a double tap in [zone] - or, on a side, any quick tap after one, each seeking again. Where
     * [undoesTap], the tap before it was taken as a tap of its own and acted on at once; its action is undone.
     */
    data class Double(val zone: TapZone, val undoesTap: Boolean) : TapOutcome
}

/**
 * Tells taps apart as they lift, without ever holding one back to see whether another follows: a tap acts the
 * moment it lifts, and one that turns out to be the first of a double tap is undone by the second.
 *
 * A double tap on a side starts a series - every quick tap after it seeks again, as video players do - which a
 * slow tap, a tap in the middle or a long press ends. A double tap in the middle stands alone: a third tap is
 * a tap of its own.
 */
internal class TapSequence(private val doubleTapTimeoutMs: Long) {

    /** When the last tap lifted, while another may still follow it; null when none may. */
    private var lastUpMs: Long? = null

    /** Whether quick taps on a side are seeking - see [TapSequence]. */
    private var isSeeking = false

    /**
     * The tap that went down at [downMs] and lifted at [upMs] in [zone]. Without [answersDoubleTap], every tap is
     * a tap of its own.
     */
    fun onTap(downMs: Long, upMs: Long, zone: TapZone, answersDoubleTap: Boolean): TapOutcome {
        val previousUpMs = lastUpMs
        val followsTap = answersDoubleTap && previousUpMs != null && downMs - previousUpMs < doubleTapTimeoutMs
        val outcome = when {
            !followsTap || (isSeeking && zone == TapZone.CENTER) -> TapOutcome.Single
            isSeeking -> TapOutcome.Double(zone, undoesTap = false)
            else -> TapOutcome.Double(zone, undoesTap = true)
        }
        val isSideDoubleTap = outcome is TapOutcome.Double && zone != TapZone.CENTER
        isSeeking = isSideDoubleTap
        lastUpMs = if (outcome is TapOutcome.Double && !isSideDoubleTap) null else upMs
        return outcome
    }

    /** A gesture other than a tap - a long press - ends whatever taps were under way. */
    fun reset() {
        lastUpMs = null
        isSeeking = false
    }
}
