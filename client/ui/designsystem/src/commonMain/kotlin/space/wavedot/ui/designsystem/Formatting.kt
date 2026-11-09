package space.wavedot.ui.designsystem

import kotlin.time.Duration

fun Duration.toClock(): String {
    val totalSeconds = inWholeSeconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = if (hours > 0) minutes.toString().padStart(2, '0') else minutes.toString()
    val prefix = if (hours > 0) "$hours:" else ""
    return "$prefix$mm:${seconds.toString().padStart(2, '0')}"
}

fun Duration.toLength(): String {
    val totalMinutes = inWholeMinutes.coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours h $minutes min"
        hours > 0 -> "$hours h"
        else -> "$minutes min"
    }
}

fun trackCount(count: Int): String = if (count == 1) "1 track" else "$count tracks"
