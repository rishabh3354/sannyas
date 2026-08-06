package com.osho.player.core.common

import java.util.Locale
import kotlin.math.roundToInt

/** Parsing and formatting helpers for the `HH:MM:SS` values used by the catalog. */
object DurationFormat {

    /** Accepts `HH:MM:SS`, `MM:SS` or a bare second count. Returns 0 when unparseable. */
    fun parseToSeconds(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val parts = raw.trim().split(':').mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    /** `1:04:09` / `4:09` — the compact form used next to progress bars. */
    fun clock(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val totalSeconds = safe / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /**
     * Splits a runtime into whole hours and the remaining whole minutes.
     *
     * The wording that goes around these numbers is a translated resource, so
     * only the arithmetic lives here — see [com.osho.player.core.common.compactDuration].
     * A talk under a minute reports one minute rather than zero, because "0 min"
     * next to a real recording reads as a bug.
     */
    fun hoursAndMinutes(seconds: Long): Pair<Int, Int> {
        val safe = seconds.coerceAtLeast(0L)
        val hours = (safe / 3600).toInt()
        val minutes = ((safe % 3600) / 60).toInt()
        return if (hours == 0) hours to minutes.coerceAtLeast(1) else hours to minutes
    }

    /** `62%` style label for a resume position. */
    fun percent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).roundToInt()}%"
}

/** Human-readable byte counts for the storage row in Downloads. */
fun Long.formatBytes(): String {
    val bytes = coerceAtLeast(0L)
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, if (value >= 10) "%.0f %s" else "%.1f %s", value, units[unit])
}
