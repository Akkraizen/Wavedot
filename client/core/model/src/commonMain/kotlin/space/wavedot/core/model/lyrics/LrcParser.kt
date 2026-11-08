package space.wavedot.core.model.lyrics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object LrcParser {
    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val METADATA = Regex("""^\[[a-zA-Z]+:.*]$""")

    fun parse(text: String, provider: String, credits: String? = null): Lyrics {
        val offset = OFFSET.find(text)?.groupValues?.get(1)?.toLongOrNull()?.milliseconds ?: Duration.ZERO
        val timed = mutableListOf<LyricLine>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            val stamps = TIMESTAMP.findAll(line).toList()
            if (stamps.isEmpty() || stamps.first().range.first != 0) return@forEach
            val content = line.substring(stamps.last().range.last + 1).trim()
            stamps.forEach { stamp ->
                val start = (toDuration(stamp) - offset).coerceAtLeast(Duration.ZERO)
                timed += LyricLine(start, content)
            }
        }
        if (timed.isEmpty()) {
            val plain = text.lineSequence().filterNot { METADATA.matches(it.trim()) }.joinToString("\n")
            return Lyrics.plain(plain, provider, credits)
        }
        return Lyrics(timed.sortedBy { it.start }, synced = true, provider = provider, credits = credits)
    }

    private fun toDuration(match: MatchResult): Duration {
        val (minutes, seconds, fraction) = match.destructured
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return (minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + millis).milliseconds
    }
}
