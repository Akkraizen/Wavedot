package space.wavedot.core.model

object ArtworkReference {
    private const val PREFIX = "wavedot-artwork:"

    fun of(source: SourceId, key: String): String = PREFIX + MediaId(source, key)

    fun matches(url: String): Boolean = url.startsWith(PREFIX)

    fun parse(url: String): MediaId? =
        if (matches(url)) runCatching { MediaId.parse(url.removePrefix(PREFIX)) }.getOrNull() else null
}
