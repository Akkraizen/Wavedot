package space.wavedot.playback.api

import space.wavedot.core.model.stream.StreamSource

interface StreamProxy : AutoCloseable {
    suspend fun publish(source: StreamSource.Custom): String

    fun revoke(url: String)
}

suspend fun StreamSource.toPlayableUri(proxy: StreamProxy): PlayableUri = when (this) {
    is StreamSource.LocalFile -> PlayableUri(path, emptyMap(), mimeType, isLocal = true)
    is StreamSource.Remote -> PlayableUri(url, headers, mimeType, isLocal = false)
    is StreamSource.Custom -> PlayableUri(proxy.publish(this), emptyMap(), mimeType, isLocal = false)
}

data class PlayableUri(
    val value: String,
    val headers: Map<String, String>,
    val mimeType: String?,
    val isLocal: Boolean,
)
