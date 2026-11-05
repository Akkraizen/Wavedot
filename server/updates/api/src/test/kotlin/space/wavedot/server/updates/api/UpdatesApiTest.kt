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
    private val adminToken = "admin-secret"
    private val binary = ByteArray(5000) { (it % 97).toByte() }

    private fun ApplicationTestBuilder.setUp(): HttpClient {
        val db = TestDatabase("classpath:db/migration/updates")
        val repository = ExposedReleaseRepository(db.database)
        val storage = FileSystemArtifactStorage(Files.createTempDirectory("artifacts"))
        val useCases = UpdatesUseCases(
            resolveLatest = ResolveLatest(repository),
            publish = PublishRelease(repository, storage, UuidGenerator, MutableClock()),
            yank = YankRelease(repository),
            releases = repository,
            artifactFile = { storage.resolve(it).toFile() },
            adminToken = adminToken,
        )
        application {
            installServiceDefaults("updates-test")
            updatesApi(useCases)
        }
        return createClient { install(ContentNegotiation) { json(ProtocolJson) } }
    }

    private suspend fun HttpClient.publish(version: String, token: String = adminToken) = post("/v1/admin/releases") {
        bearerAuth(token)
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("metadata", ProtocolJson.encodeToString(PublishReleaseRequest(version, ChannelDto.STABLE, "Fixes")))
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
}
