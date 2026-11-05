package space.wavedot.server.hub.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.core.ProblemDetails
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.hub.ChangeDto
import space.wavedot.protocol.hub.CreateAccountRequest
import space.wavedot.protocol.hub.CreateAccountResponse
import space.wavedot.protocol.hub.PullChangesResponse
import space.wavedot.protocol.hub.PushChangesRequest
import space.wavedot.protocol.hub.PushChangesResponse
import space.wavedot.server.hub.data.ExposedAccountRepository
import space.wavedot.server.hub.data.ExposedDeviceRepository
import space.wavedot.server.hub.data.ExposedLibraryRepository
import space.wavedot.server.hub.data.Sha256TokenService
import space.wavedot.server.hub.domain.account.AuthenticateDevice
import space.wavedot.server.hub.domain.account.CreateAccount
import space.wavedot.server.hub.domain.sync.CollectionRegistry
import space.wavedot.server.hub.domain.sync.PullChanges
import space.wavedot.server.hub.domain.sync.PushChanges
import space.wavedot.server.kernel.UuidGenerator
import space.wavedot.server.ktor.installServiceDefaults
import space.wavedot.server.persistence.ExposedTransactionRunner
import space.wavedot.server.testing.MutableClock
import space.wavedot.server.testing.TestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HubApiTest {
    private fun ApplicationTestBuilder.setUp(): HttpClient {
        val db = TestDatabase("classpath:db/migration/hub")
        val accounts = ExposedAccountRepository(db.database)
        val devices = ExposedDeviceRepository(db.database)
        val library = ExposedLibraryRepository(db.database)
        val tokens = Sha256TokenService()
        val transactions = ExposedTransactionRunner(db.database)
        val clock = MutableClock()
        val events = RevisionEvents()
        val useCases = HubUseCases(
            createAccount = CreateAccount(accounts, devices, tokens, UuidGenerator, transactions, clock),
            authenticate = AuthenticateDevice(devices, tokens, clock),
            push = PushChanges(accounts, library, CollectionRegistry.default(), transactions, events),
            pull = PullChanges(library),
            events = events,
        )
        application {
            installServiceDefaults("hub-test")
            hubApi(useCases)
        }
        return createClient { install(ContentNegotiation) { json(ProtocolJson) } }
    }

    @Test
    fun registerPushAndPull() = testApplication {
        val client = setUp()
        val created = client.post("/v1/accounts") {
            contentType(ContentType.Application.Json)
            setBody(CreateAccountRequest("Desk", PlatformDto.WINDOWS))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val token = created.body<CreateAccountResponse>().deviceToken

        val payload = buildJsonObject { put("title", "Kukushka") }
        val pushed = client.post("/v1/sync/changes") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PushChangesRequest(listOf(ChangeDto("favorites", "yandex-music:1", false, payload, 10))))
        }.body<PushChangesResponse>()
        assertEquals(1L, pushed.revision)

        val pulled = client.get("/v1/sync/changes?since=0") { bearerAuth(token) }.body<PullChangesResponse>()
        val change = pulled.changes.single().change
        assertEquals(payload, change.payload)
        assertEquals("yandex-music:1", change.key)
    }

    @Test
    fun syncRequiresValidToken() = testApplication {
        val client = setUp()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/sync/changes").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/sync/changes") { bearerAuth("forged") }.status)
    }

    @Test
    fun validationErrorsBecomeProblemDetails() = testApplication {
        val client = setUp()
        val response = client.post("/v1/accounts") {
            contentType(ContentType.Application.Json)
            setBody(CreateAccountRequest(" ", PlatformDto.ANDROID))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.body<ProblemDetails>().type.endsWith("validation"))
    }

    @Test
    fun healthEndpointsRespond() = testApplication {
        val client = setUp()
        assertEquals(HttpStatusCode.OK, client.get("/health/live").status)
    }
}
