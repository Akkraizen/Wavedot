package space.wavedot.core.domain.update

object VersionOrder {
    private val PATTERN = Regex("""^v?(\d{1,9})\.(\d{1,9})\.(\d{1,9})(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

    private data class Parsed(val numbers: List<Int>, val preRelease: List<String>?)

    fun isNewer(candidate: String, than: String): Boolean {
        val left = parse(candidate) ?: return false
        val right = parse(than) ?: return false
        return compare(left, right) > 0
    }

    private fun parse(value: String): Parsed? {
        val match = PATTERN.matchEntire(value.trim()) ?: return null
        val (major, minor, patch, pre) = match.destructured
        return Parsed(listOf(major.toInt(), minor.toInt(), patch.toInt()), pre.ifEmpty { null }?.split('.'))
    }

    private fun compare(left: Parsed, right: Parsed): Int {
        left.numbers.zip(right.numbers).forEach { (a, b) -> if (a != b) return a.compareTo(b) }
        val a = left.preRelease
        val b = right.preRelease
        return when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            else -> comparePreRelease(a, b)
        }
    }

    private fun comparePreRelease(left: List<String>, right: List<String>): Int {
        left.zip(right).forEach { (a, b) ->
            val x = a.toIntOrNull()
            val y = b.toIntOrNull()
            val result = when {
                x != null && y != null -> x.compareTo(y)
                x != null -> -1
                y != null -> 1
                else -> a.compareTo(b)
            }
            if (result != 0) return result
        }
        return left.size.compareTo(right.size)
    }
}
