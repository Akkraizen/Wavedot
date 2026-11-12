package space.wavedot.plugin.api.http

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.discard
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.common.withoutUrlSecrets
import space.wavedot.core.model.stream.ByteStream

class HttpStatusException(val status: HttpStatusCode, url: String) :
    Exception("HTTP ${status.value} for ${url.withoutUrlSecrets()}")

suspend fun HttpClient.openByteStream(
    url: String,
    offset: Long = 0,
    headers: Map<String, String> = emptyMap(),
): ByteStream {
    val ready = CompletableDeferred<Pair<ByteReadChannel, Long?>>()
    val released = CompletableDeferred<Unit>()
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val job = scope.launch {
        try {
            prepareGet(url) {
                headers.forEach { (name, value) -> header(name, value) }
                if (offset > 0) header(HttpHeaders.Range, "bytes=$offset-")
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    ready.completeExceptionally(HttpStatusException(response.status, url))
                    return@execute
                }
                val channel = response.bodyAsChannel()
                val partial = response.status == HttpStatusCode.PartialContent
                if (offset > 0 && !partial) channel.discard(offset)
                val total = totalLength(
                    contentRange = response.headers[HttpHeaders.ContentRange],
                    contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
                    offset = offset,
                    partial = partial,
                )
                ready.complete(channel to total)
                released.await()
            }
        } catch (t: Throwable) {
            ready.completeExceptionally(t)
            throw t
        }
    }
    val (channel, total) = try {
        ready.await()
    } catch (t: Throwable) {
        job.cancel()
        throw WavedotException(WavedotError.Network(t.message ?: "Stream open failed"), t)
    }
    return ChannelByteStream(channel, total, job, released)
}

internal fun totalLength(contentRange: String?, contentLength: Long?, offset: Long, partial: Boolean): Long? {
    contentRange?.substringAfterLast('/')?.toLongOrNull()?.let { return it }
    if (contentLength == null) return null
    return if (partial) contentLength + offset else contentLength
}

private class ChannelByteStream(
    private val channel: ByteReadChannel,
    override val totalLength: Long?,
    private val job: Job,
    private val released: CompletableDeferred<Unit>,
) : ByteStream {
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        channel.readAvailable(buffer, offset, length)

    override fun close() {
        released.complete(Unit)
        job.cancel()
    }
}
