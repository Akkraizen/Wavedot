package space.wavedot.infra.streamproxy

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.playback.api.StreamProxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LoopbackStreamProxy(
    private val host: String = "127.0.0.1",
    private val port: Int = 0,
) : StreamProxy {
    private val entries = ConcurrentHashMap<String, StreamSource.Custom>()
    private val mutex = Mutex()
    private var server: EmbeddedServer<*, *>? = null
    private var boundPort: Int = -1

    override suspend fun publish(source: StreamSource.Custom): String {
        val actualPort = ensureStarted()
        val token = UUID.randomUUID().toString()
        entries[token] = source
        return "http://$host:$actualPort$PATH_PREFIX$token"
    }

    override fun revoke(url: String) {
        entries.remove(url.substringAfterLast('/'))
    }

    override fun close() {
        entries.clear()
        server?.stop(0, 0)
        server = null
    }

    private suspend fun ensureStarted(): Int = mutex.withLock {
        if (server == null) {
            val created = embeddedServer(CIO, port = port, host = host) {
                routing {
                    get("$PATH_PREFIX{token}") { serve(call, includeBody = true) }
                    head("$PATH_PREFIX{token}") { serve(call, includeBody = false) }
                }
            }
            created.start(wait = false)
            boundPort = created.engine.resolvedConnectors().first().port
            server = created
        }
        boundPort
    }

    private suspend fun serve(call: ApplicationCall, includeBody: Boolean) {
        val source = call.parameters["token"]?.let(entries::get)
            ?: return call.respond(HttpStatusCode.NotFound)
        val range = ByteRange.parse(call.request.headers[HttpHeaders.Range])
        val start = range?.start ?: 0L
        val stream = source.opener.open(start)
        val total = stream.totalLength
        if (total != null && start >= total && total > 0) {
            stream.close()
            call.response.header(HttpHeaders.ContentRange, "bytes */$total")
            return call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
        }
        val end = when {
            total == null -> range?.endInclusive
            range?.endInclusive == null -> total - 1
            else -> minOf(range.endInclusive, total - 1)
        }
        val length = end?.let { it - start + 1 }
        val status = if (range != null) HttpStatusCode.PartialContent else HttpStatusCode.OK
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        if (range != null && end != null) {
            call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/${total ?: "*"}")
        }
        val contentType = source.mimeType?.let(ContentType::parse) ?: ContentType.Application.OctetStream
        if (!includeBody) {
            stream.close()
            return call.respond(object : OutgoingContent.NoContent() {
                override val status: HttpStatusCode = status
                override val contentType: ContentType = contentType
                override val contentLength: Long? = length
            })
        }
        call.respond(object : OutgoingContent.WriteChannelContent() {
            override val status: HttpStatusCode = status
            override val contentType: ContentType = contentType
            override val contentLength: Long? = length

            override suspend fun writeTo(channel: ByteWriteChannel) {
                stream.use {
                    val buffer = ByteArray(BUFFER_SIZE)
                    var remaining = length ?: Long.MAX_VALUE
                    while (remaining > 0) {
                        val read = it.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) break
                        channel.writeFully(buffer, 0, read)
                        remaining -= read
                    }
                    channel.flush()
                }
            }
        })
    }

    private companion object {
        const val PATH_PREFIX = "/stream/"
        const val BUFFER_SIZE = 64 * 1024
    }
}
