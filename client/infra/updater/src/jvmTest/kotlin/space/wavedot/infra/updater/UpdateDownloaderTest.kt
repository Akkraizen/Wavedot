package space.wavedot.infra.updater

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.domain.update.AvailableUpdate
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.updates.UpdateManifest
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateDownloaderTest {
    private val payload = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    private val directory: File = Files.createTempDirectory("update").toFile()
    private val target = File(directory, "wavedot.msi")
    private val downloader = UpdateDownloader(HttpClient(MockEngine { respond(payload, HttpStatusCode.OK) }))

    private fun update(size: Long = payload.size.toLong(), sha256: String = sha, url: String = "https://updates.test/a") =
        AvailableUpdate("0.2.0", "", false, url, sha256, size, "wavedot.msi")

    private suspend fun assertRejected(update: AvailableUpdate) {
        val error = assertFailsWith<WavedotException> { downloader.download(update, target) }
        assertIs<WavedotError.Invalid>(error.error)
        assertFalse(target.exists())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun storesAVerifiedDownload() = runTest {
        val progress = mutableListOf<Long>()
        val file = downloader.download(update(), target) { received, _ -> progress += received }
        assertContentEquals(payload, file.readBytes())
        assertEquals(payload.size.toLong(), progress.last())
    }

    @Test
    fun rejectsWrongChecksumsSizesAndInsecureUrls() = runTest {
        assertRejected(update(sha256 = "00"))
        assertRejected(update(size = 1000))
        assertRejected(update(size = payload.size + 1L))
        assertRejected(update(size = 0))
        assertRejected(update(url = "http://updates.test/a"))
    }

    @Test
    fun verifiesEcdsaSignaturesAgainstTrustedKeys() {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val trusted = generator.generateKeyPair()
        val other = generator.generateKeyPair()
        val manifest = UpdateManifest.canonical("0.2.0", PlatformDto.WINDOWS, ArchDto.X64, "wavedot.msi", 10, "abc")
        fun sign(key: java.security.PrivateKey, text: String) = Base64.getEncoder().encodeToString(
            Signature.getInstance(UpdateManifest.SIGNATURE_ALGORITHM).run {
                initSign(key)
                update(text.toByteArray())
                sign()
            },
        )
        val encodedKey = Base64.getEncoder().encodeToString(trusted.public.encoded)
        val verifier = EcdsaUpdateSignatureVerifier(listOf("", encodedKey))

        assertTrue(verifier.isTrusted(manifest, sign(trusted.private, manifest)))
        assertFalse(verifier.isTrusted(manifest, sign(other.private, manifest)))
        assertFalse(verifier.isTrusted(manifest + "x", sign(trusted.private, manifest)))
        assertFalse(verifier.isTrusted(manifest, "not base64!"))
        assertFalse(EcdsaUpdateSignatureVerifier(emptyList()).isTrusted(manifest, sign(trusted.private, manifest)))
    }
}
