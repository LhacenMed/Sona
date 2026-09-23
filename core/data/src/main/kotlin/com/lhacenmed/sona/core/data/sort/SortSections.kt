package com.lhacenmed.sona.core.data.sort

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/*
 * How a number a list is sorted by names the section a row sits in - what a fast scroller's popup
 * shows. Auxio's, one per kind of number its lists sort by.
 */

/** A number as its own section: a year, a count - Auxio's `fmt_number`. */
internal val NumberSection: (Long) -> String = Long::toString

/** A duration's rough size - under a minute, whole minutes, then whole hours: Auxio's `formatDurationMsPopup`. */
internal fun durationSection(durationMs: Long): String {
    val totalMinutes = Math.floorDiv(durationMs, 60_000L)
    val totalHours = totalMinutes / 60
    val format = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.NARROW)
    return when {
        totalMinutes < 1 -> "<" + format.format(Measure(1, MeasureUnit.MINUTE))
        totalHours < 1 -> format.format(Measure(totalMinutes, MeasureUnit.MINUTE))
        else -> format.format(Measure(totalHours, MeasureUnit.HOUR))
    }
}

/** The year a moment given in seconds since the epoch falls in - how Auxio names a date-added section. */
internal fun yearOfEpochSecondsSection(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).year.toString()
