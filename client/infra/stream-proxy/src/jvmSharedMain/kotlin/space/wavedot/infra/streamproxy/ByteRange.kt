package space.wavedot.infra.streamproxy

internal data class ByteRange(val start: Long, val endInclusive: Long?) {
    companion object {
        fun parse(header: String?): ByteRange? {
            if (header == null || !header.startsWith("bytes=")) return null
            val spec = header.removePrefix("bytes=").substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash < 0) return null
            val startText = spec.substring(0, dash)
            val endText = spec.substring(dash + 1)
            if (startText.isEmpty()) return null
            val start = startText.toLongOrNull() ?: return null
            val end = if (endText.isEmpty()) null else endText.toLongOrNull() ?: return null
            if (end != null && end < start) return null
            return ByteRange(start, end)
        }
    }
}
