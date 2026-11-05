package space.wavedot.infra.streamproxy

import kotlinx.coroutines.runBlocking
import space.wavedot.core.model.stream.ByteStream
import space.wavedot.core.model.stream.ByteStreamOpener
import space.wavedot.core.model.stream.StreamSource
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoopbackStreamProxyTest {
    private val payload = ByteArray(200_000) { (it * 31 % 256).toByte() }
    private val proxy = LoopbackStreamProxy()

    private val source = StreamSource.Custom("audio/flac", ByteStreamOpener { offset -> memoryStream(offset) })

    private fun memoryStream(offset: Long): ByteStream = object : ByteStream {
        private var position = offset.toInt()
        override val totalLength: Long = payload.size.toLong()

        override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= payload.size) return -1
            val count = minOf(length, payload.size - position)
            payload.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun close() = Unit
    }

    @AfterTest
    fun tearDown() = proxy.close()

    private fun open(url: String, range: String? = null): HttpURLConnection =
        (URI(url).toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
            range?.let { setRequestProperty("Range", it) }
        }

    @Test
    fun servesFullContent() = runBlocking {
        val url = proxy.publish(source)
        val connection = open(url)
        assertEquals(200, connection.responseCode)
        assertEquals("audio/flac", connection.contentType)
        assertEquals("bytes", connection.getHeaderField("Accept-Ranges"))
        assertContentEquals(payload, connection.inputStream.readBytes())
    }

    @Test
    fun servesRequestedRange() = runBlocking {
        val url = proxy.publish(source)
        val connection = open(url, "bytes=1000-1999")
        assertEquals(206, connection.responseCode)
        assertEquals("bytes 1000-1999/200000", connection.getHeaderField("Content-Range"))
        assertContentEquals(payload.copyOfRange(1000, 2000), connection.inputStream.readBytes())
    }

    @Test
    fun servesOpenEndedRange() = runBlocking {
        val url = proxy.publish(source)
        val connection = open(url, "bytes=150000-")
        assertEquals(206, connection.responseCode)
        assertContentEquals(payload.copyOfRange(150000, payload.size), connection.inputStream.readBytes())
    }

    @Test
    fun revokedStreamsAreGone() = runBlocking {
        val url = proxy.publish(source)
        proxy.revoke(url)
        assertEquals(404, open(url).responseCode)
    }

    @Test
    fun parsesRanges() {
        assertEquals(ByteRange(5, null), ByteRange.parse("bytes=5-"))
        assertEquals(ByteRange(5, 9), ByteRange.parse("bytes=5-9"))
        assertNull(ByteRange.parse("bytes=-500"))
        assertNull(ByteRange.parse("items=1-2"))
    }
}
