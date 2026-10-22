package com.lhacenmed.sona.core.common.format

import java.util.Locale

private const val BYTES_PER_KB = 1024.0

/** "m:ss", or "h:mm:ss" from the hour on. */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/** "3.4 MB"/"512 KB" - a file size in whichever unit reads best. */
fun formatFileSize(bytes: Long): String {
    val kb = bytes / BYTES_PER_KB
    val mb = kb / BYTES_PER_KB
    return when {
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
        else -> "$bytes B"
    }
}
