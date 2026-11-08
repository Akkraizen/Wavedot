package space.wavedot.core.model.lyrics

import kotlin.time.Duration

data class LyricLine(
    val start: Duration?,
    val text: String,
)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val provider: String,
    val credits: String? = null,
) {
    val isEmpty: Boolean get() = lines.all { it.text.isBlank() }

    fun activeLineIndex(position: Duration): Int {
        if (!synced || lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var answer = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = lines[mid].start ?: Duration.ZERO
            if (start <= position) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }

    companion object {
        fun plain(text: String, provider: String, credits: String? = null): Lyrics =
            Lyrics(text.lines().map { LyricLine(null, it.trimEnd()) }.trimBlankEdges(), synced = false, provider, credits)
    }
}

internal fun List<LyricLine>.trimBlankEdges(): List<LyricLine> =
    dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
