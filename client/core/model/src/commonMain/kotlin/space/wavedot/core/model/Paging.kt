package space.wavedot.core.model

import kotlin.jvm.JvmInline

@JvmInline
value class Cursor(val value: String)

data class Page<out T>(
    val items: List<T>,
    val next: Cursor? = null,
) {
    val hasMore: Boolean get() = next != null

    companion object {
        fun <T> empty(): Page<T> = Page(emptyList())
    }
}
