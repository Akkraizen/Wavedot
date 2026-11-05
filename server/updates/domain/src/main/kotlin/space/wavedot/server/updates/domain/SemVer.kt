package space.wavedot.server.updates.domain

import space.wavedot.server.kernel.AppError

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch).let { if (it != 0) return it }
        return when {
            preRelease == other.preRelease -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    override fun toString(): String = "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(value: String): SemVer {
            val match = PATTERN.matchEntire(value.trim()) ?: throw AppError.Validation("Invalid version: $value")
            val (major, minor, patch, pre) = match.destructured
            return SemVer(major.toInt(), minor.toInt(), patch.toInt(), pre.ifEmpty { null })
        }

        private fun comparePreRelease(left: String, right: String): Int {
            val a = left.split('.')
            val b = right.split('.')
            for (i in 0 until minOf(a.size, b.size)) {
                val x = a[i].toIntOrNull()
                val y = b[i].toIntOrNull()
                val result = when {
                    x != null && y != null -> x.compareTo(y)
                    x != null -> -1
                    y != null -> 1
                    else -> a[i].compareTo(b[i])
                }
                if (result != 0) return result
            }
            return a.size.compareTo(b.size)
        }
    }
}
