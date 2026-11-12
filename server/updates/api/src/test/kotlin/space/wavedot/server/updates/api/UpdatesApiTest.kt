package space.wavedot.server.updates.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.updates.UpdateManifest
import space.wavedot.server.updates.domain.GetArtifact
import space.wavedot.server.updates.domain.ReleaseCatalog
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.assertFailsWith
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.updates.ChannelDto
import space.wavedot.protocol.updates.PublishReleaseRequest
import space.wavedot.protocol.updates.PublishedReleaseDto
import space.wavedot.protocol.updates.ReleaseDto
import space.wavedot.server.kernel.UuidGenerator
import space.wavedot.server.ktor.installServiceDefaults
import space.wavedot.server.testing.MutableClock
import space.wavedot.server.testing.TestDatabase
import space.wavedot.server.updates.data.ExposedReleaseRepository
import space.wavedot.server.updates.data.FileSystemArtifactStorage
import space.wavedot.server.updates.domain.PublishRelease
import space.wavedot.server.updates.domain.ResolveLatest
import space.wavedot.server.updates.domain.YankRelease
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdatesApiTest {
    private val adminToken = "admin-secret-with-at-least-32-characters"
    private val binary = ByteArray(5000) { (it % 97).toByte() }
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)

    private fun ApplicationTestBuilder.setUp(
        signingKey: String? = null,
        token: String = adminToken,
        limits: UpdatesApiLimits = UpdatesApiLimits(),
    ): HttpClient {
        val db = TestDatabase("classpath:db/migration/updates")
        val repository = ExposedReleaseRepository(db.database)
        val storage = FileSystemArtifactStorage(Files.createTempDirectory("artifacts"))
        val clock = MutableClock()
        val catalog = ReleaseCatalog(repository, clock)
        val useCases = UpdatesUseCases(
            resolveLatest = ResolveLatest(catalog),
            publish = PublishRelease(repository, storage, UuidGenerator, clock, catalog, signingKey?.let(::EcdsaArtifactSignatureVerifier)),
            yank = YankRelease(repository, catalog),
            getArtifact = GetArtifact(repository, storage),
            adminToken = token,
        )
        application {
            installServiceDefaults("updates-test")
            updatesApi(useCases, limits)
        }
        return createClient { install(ContentNegotiation) { json(ProtocolJson) } }
    }

    private fun sign(version: String, fileName: String, content: ByteArray = binary): String {
        val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
        val manifest = UpdateManifest.canonical(version, PlatformDto.WINDOWS, ArchDto.X64, fileName, content.size.toLong(), sha)
        val signature = Signature.getInstance(UpdateManifest.SIGNATURE_ALGORITHM).run {
            initSign(keys.private)
            update(manifest.toByteArray())
            sign()
        }
        return Base64.getEncoder().encodeToString(signature)
    }

    private suspend fun HttpClient.publish(version: String, token: String = adminToken, signature: String? = null) = post("/v1/admin/releases") {
        bearerAuth(token)
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("metadata", ProtocolJson.encodeToString(PublishReleaseRequest(version, ChannelDto.STABLE, "Fixes")))
                    signature?.let { append("signature:windows:x64", it) }
                    append(
                        "artifact:windows:x64",
                        binary,
                        Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"wavedot-$version.msi\"") },
                    )
                },
            ),
        )
    }

    @Test
    fun publishResolveAndDownload() = testApplication {
        val client = setUp()
        val published = client.publish("0.2.0")
        assertEquals(HttpStatusCode.Created, published.status)
        val id = published.body<PublishedReleaseDto>().id

        val latest = client.get("/v1/releases/latest?platform=windows&arch=x64&channel=stable&current=0.1.0&installId=a")
        assertEquals(HttpStatusCode.OK, latest.status)
        val release = latest.body<ReleaseDto>()
        assertEquals("0.2.0", release.version)
        val sha = MessageDigest.getInstance("SHA-256").digest(binary).joinToString("") { "%02x".format(it) }
        assertEquals(sha, release.artifact.sha256)

        val partial = client.get(release.artifact.url) { header(HttpHeaders.Range, "bytes=100-199") }
        assertEquals(HttpStatusCode.PartialContent, partial.status)

        assertEquals(HttpStatusCode.NoContent, client.post("/v1/admin/releases/$id/yank") { bearerAuth(adminToken) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get(release.artifact.url).status)
        assertEquals(
            HttpStatusCode.NoContent,
            client.get("/v1/releases/latest?platform=windows&arch=x64&current=0.1.0").status,
        )
    }

    @Test
    fun upToDateClientsGetNoContent() = testApplication {
        val client = setUp()
        client.publish("0.2.0")
        assertEquals(HttpStatusCode.NoContent, client.get("/v1/releases/latest?platform=windows&arch=x64&current=0.2.0").status)
    }

    @Test
    fun adminRoutesRequireToken() = testApplication {
        val client = setUp()
        assertEquals(HttpStatusCode.Unauthorized, client.publish("0.2.0", token = "wrong").status)
    }

    @Test
    fun invalidQueryIsRejected() = testApplication {
        val client = setUp()
        val response = client.get("/v1/releases/latest?platform=amiga&current=0.1.0")
        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
    }

    @Test
    fun signedReleasesCarryTheirSignatureToClients() = testApplication {
        val client = setUp(signingKey = publicKey)
        assertEquals(HttpStatusCode.BadRequest, client.publish("0.2.0").status)
        assertEquals(HttpStatusCode.BadRequest, client.publish("0.2.0", signature = sign("0.2.1", "wavedot-0.2.0.msi")).status)

        val signature = sign("0.2.0", "wavedot-0.2.0.msi")
        assertEquals(HttpStatusCode.Created, client.publish("0.2.0", signature = signature).status)
        val release = client.get("/v1/releases/latest?platform=windows&arch=x64&current=0.1.0").body<ReleaseDto>()
        assertEquals(signature, release.artifact.signature)
    }

    @Test
    fun shortAdminTokensAreRefusedAtStartup() {
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                setUp(token = "change-me-too")
                startApplication()
            }
        }
    }

    @Test
    fun adminRoutesStayClosedWithoutAToken() = testApplication {
        val client = setUp(token = "")
        assertEquals(HttpStatusCode.Unauthorized, client.publish("0.2.0", token = "").status)
    }

    @Test
    fun publicEndpointsAreRateLimited() = testApplication {
        val client = setUp(limits = UpdatesApiLimits(publicRequestsPerMinute = 2))
        val url = "/v1/releases/latest?platform=windows&arch=x64&current=0.1.0"
        assertEquals(HttpStatusCode.NoContent, client.get(url).status)
        assertEquals(HttpStatusCode.NoContent, client.get(url).status)
        assertEquals(HttpStatusCode.TooManyRequests, client.get(url).status)
    }

    @Test
    fun uploadsBeyondTheLimitAreRefused() = testApplication {
        val client = setUp(limits = UpdatesApiLimits(maxUploadBytes = 1024))
        assertEquals(HttpStatusCode.PayloadTooLarge, client.publish("0.2.0").status)
    }
}
