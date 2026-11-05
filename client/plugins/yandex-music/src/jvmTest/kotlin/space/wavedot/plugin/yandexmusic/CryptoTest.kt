package space.wavedot.plugin.yandexmusic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import space.wavedot.plugin.yandexmusic.api.ClientProfile
import space.wavedot.plugin.yandexmusic.api.FileInfoSigner
import space.wavedot.plugin.yandexmusic.api.YandexMusicApi
import space.wavedot.plugin.yandexmusic.stream.EncryptedStreamOpener
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CryptoTest {
    @Test
    fun signatureMatchesReferenceImplementation() {
        val sign = FileInfoSigner.sign(
            ClientProfile.ANDROID.signKey, 1_700_000_000, "12345", "lossless", YandexMusicApi.CODECS, "encraw",
        )
        assertEquals("A8rhGDs/OL1IOCIm7vYn4rj4ieadMfXvKJn2YoqVtoc", sign)
    }

    @Test
    fun decryptsFromArbitraryOffsets() = runTest {
        val key = ByteArray(16) { it.toByte() }
        val plain = Random(7).nextBytes(10_000)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        val encrypted = cipher.doFinal(plain)

        val http = HttpClient(MockEngine { request ->
            val start = request.headers[HttpHeaders.Range]?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            respond(
                encrypted.copyOfRange(start, encrypted.size),
                if (start > 0) HttpStatusCode.PartialContent else HttpStatusCode.OK,
                headersOf(
                    HttpHeaders.ContentRange to listOf("bytes $start-${encrypted.size - 1}/${encrypted.size}"),
                    HttpHeaders.ContentLength to listOf((encrypted.size - start).toString()),
                ),
            )
        })
        val opener = EncryptedStreamOpener(http, "https://strm.test/file", key.joinToString("") { "%02x".format(it) })

        for (offset in listOf(0L, 15L, 16L, 17L, 4_099L, 9_999L)) {
            val stream = opener.open(offset)
            assertEquals(10_000L, stream.totalLength)
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(777)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            stream.close()
            assertContentEquals(plain.copyOfRange(offset.toInt(), plain.size), out.toByteArray(), "offset $offset")
        }
    }
}
