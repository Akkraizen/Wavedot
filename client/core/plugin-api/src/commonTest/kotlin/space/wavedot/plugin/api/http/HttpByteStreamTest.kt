package space.wavedot.plugin.api.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import space.wavedot.core.model.stream.ByteStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HttpByteStreamTest {
    private val payload = ByteArray(1000) { (it % 251).toByte() }

    private val client = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val range = request.headers[HttpHeaders.Range]
                if (range == null) {
                    respond(payload, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, payload.size.toString()))
                } else {
                    val start = range.removePrefix("bytes=").removeSuffix("-").toInt()
                    respond(
                        payload.copyOfRange(start, payload.size),
                        HttpStatusCode.PartialContent,
                        headersOf(
                            HttpHeaders.ContentRange to listOf("bytes $start-${payload.size - 1}/${payload.size}"),
                            HttpHeaders.ContentLength to listOf((payload.size - start).toString()),
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun readsWholeBody() = runTest {
        val stream = client.openByteStream("https://example.test/file")
        assertEquals(1000L, stream.totalLength)
        assertContentEquals(payload, stream.readAll())
    }

    @Test
    fun readsFromOffsetUsingRange() = runTest {
        val stream = client.openByteStream("https://example.test/file", offset = 600)
        assertEquals(1000L, stream.totalLength)
        assertContentEquals(payload.copyOfRange(600, 1000), stream.readAll())
    }

    @Test
    fun computesTotalLengthFromHeaders() {
        assertEquals(500L, totalLength("bytes 100-499/500", 400, 100, partial = true))
        assertEquals(500L, totalLength(null, 400, 100, partial = true))
        assertEquals(400L, totalLength(null, 400, 0, partial = false))
    }

    private suspend fun ByteStream.readAll(): ByteArray {
        val out = ArrayList<Byte>()
        val buffer = ByteArray(128)
        use {
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                for (i in 0 until read) out += buffer[i]
            }
        }
        return out.toByteArray()
    }
}
