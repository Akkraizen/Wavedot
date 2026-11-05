package space.wavedot.core.model

import kotlin.jvm.JvmInline

@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.isNotBlank() && ':' !in value) { "Invalid source id: $value" }
    }

    override fun toString(): String = value
}

data class MediaId(val source: SourceId, val key: String) {
    init {
        require(key.isNotEmpty()) { "Media key must not be empty" }
    }

    override fun toString(): String = "${source.value}:$key"

    companion object {
        fun parse(value: String): MediaId {
            val separator = value.indexOf(':')
            require(separator > 0 && separator < value.length - 1) { "Invalid media id: $value" }
            return MediaId(SourceId(value.substring(0, separator)), value.substring(separator + 1))
        }
    }
}

enum class EntityKind { TRACK, ALBUM, ARTIST, PLAYLIST }
