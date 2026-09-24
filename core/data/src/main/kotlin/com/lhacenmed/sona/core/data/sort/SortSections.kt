package com.lhacenmed.sona.core.data.sort

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

/**
 * The month a moment given in seconds since the epoch falls in, over its year - "Aug" above "2026" -
 * which is how a date-added section is named. Auxio names it by the year alone, which puts a whole
 * year of additions under one label. Two lines rather than one, so the popup can show both large; the
 * month is abbreviated the way the user's locale abbreviates it standing on its own.
 */
internal fun monthOfEpochSecondsSection(epochSeconds: Long): String {
    val date = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault())
    val month = DateTimeFormatter.ofPattern("LLL", Locale.getDefault()).format(date)
    return "$month\n${date.year}"
}
