package space.wavedot.plugin.yandexmusic.stream

import io.ktor.client.HttpClient
import space.wavedot.core.model.stream.ByteStream
import space.wavedot.core.model.stream.ByteStreamOpener
import space.wavedot.plugin.api.http.openByteStream
import space.wavedot.plugin.yandexmusic.crypto.CtrDecryptor
import space.wavedot.plugin.yandexmusic.crypto.PlatformCrypto
import space.wavedot.plugin.yandexmusic.crypto.hexToBytes

internal class EncryptedStreamOpener(
    private val http: HttpClient,
    private val url: String,
    hexKey: String,
) : ByteStreamOpener {
    private val key = hexKey.hexToBytes()

    override suspend fun open(offset: Long): ByteStream {
        val skip = (offset % BLOCK_SIZE).toInt()
        val aligned = offset - skip
        val inner = http.openByteStream(url, aligned)
        val decryptor = PlatformCrypto.aesCtrDecryptor(key, aligned / BLOCK_SIZE)
        return DecryptingByteStream(inner, decryptor, skip)
    }

    private class DecryptingByteStream(
        private val inner: ByteStream,
        private val decryptor: CtrDecryptor,
        private var skip: Int,
    ) : ByteStream {
        override val totalLength: Long? get() = inner.totalLength

        override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            while (skip > 0) {
                val scratch = ByteArray(skip)
                val read = inner.read(scratch, 0, skip)
                if (read < 0) return -1
                decryptor.update(scratch, 0, read)
                skip -= read
            }
            val read = inner.read(buffer, offset, length)
            if (read > 0) decryptor.update(buffer, offset, read)
            return read
        }

        override fun close() = inner.close()
    }

    private companion object {
        const val BLOCK_SIZE = 16L
    }
}
