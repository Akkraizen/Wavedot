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
