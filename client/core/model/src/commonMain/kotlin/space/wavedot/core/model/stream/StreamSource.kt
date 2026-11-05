package space.wavedot.core.model.stream

sealed interface StreamSource {
    val mimeType: String?

    data class LocalFile(
        val path: String,
        override val mimeType: String? = null,
    ) : StreamSource

    data class Remote(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        override val mimeType: String? = null,
    ) : StreamSource

    class Custom(
        override val mimeType: String?,
        val opener: ByteStreamOpener,
    ) : StreamSource
}

fun interface ByteStreamOpener {
    suspend fun open(offset: Long): ByteStream
}

interface ByteStream : AutoCloseable {
    val totalLength: Long?

    suspend fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int
}
