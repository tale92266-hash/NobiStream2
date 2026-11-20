package com.nobi.nobistream2.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small pure formatting helpers shared by list rows and the player. */
object FormatUtils {

    fun formatDuration(totalSeconds: Int): String {
        if (totalSeconds <= 0) return "0:00"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        return if (unitIndex == 0) {
            String.format(Locale.US, "%d %s", bytes, units[unitIndex])
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unitIndex])
        }
    }

    fun formatDate(epochSeconds: Long): String {
        if (epochSeconds <= 0L) return ""
        val format = SimpleDateFormat("d MMM yyyy", Locale.US)
        return format.format(Date(epochSeconds * 1000L))
    }

    fun formatResumePosition(positionSeconds: Long, durationSeconds: Int): String {
        return formatDuration(positionSeconds.toInt()) + " / " + formatDuration(durationSeconds)
    }
}
