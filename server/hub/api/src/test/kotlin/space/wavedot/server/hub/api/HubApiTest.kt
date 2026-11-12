package space.wavedot.server.hub.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import space.wavedot.protocol.core.ProtocolVersion
import space.wavedot.protocol.hub.DevicesResponse
import space.wavedot.protocol.hub.RevisionEvent
import space.wavedot.server.hub.data.InMemoryRevisionFeed
import space.wavedot.server.hub.domain.account.DeleteAccount
import space.wavedot.server.hub.domain.account.ListDevices
import space.wavedot.server.hub.domain.account.RegistrationMode
import space.wavedot.server.hub.domain.account.RegistrationPolicy
import space.wavedot.server.hub.domain.account.RevokeDevice
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
    private fun ApplicationTestBuilder.setUp(
        registration: RegistrationPolicy = RegistrationPolicy.OPEN,
        limits: HubApiLimits = HubApiLimits(),
    ): HttpClient {
        val db = TestDatabase("classpath:db/migration/hub")
        val accounts = ExposedAccountRepository(db.database)
        val devices = ExposedDeviceRepository(db.database)
        val library = ExposedLibraryRepository(db.database)
        val tokens = Sha256TokenService()
        val transactions = ExposedTransactionRunner(db.database)
        val clock = MutableClock()
        val events = InMemoryRevisionFeed()
        val useCases = HubUseCases(
            createAccount = CreateAccount(accounts, devices, tokens, UuidGenerator, transactions, clock, registration),
            authenticate = AuthenticateDevice(devices, tokens, clock),
            push = PushChanges(accounts, library, CollectionRegistry.default(), transactions, events, clock),
            pull = PullChanges(library),
            events = events,
            listDevices = ListDevices(devices),
            revokeDevice = RevokeDevice(devices),
            deleteAccount = DeleteAccount(accounts),
        )
        application {
            installServiceDefaults("hub-test")
            hubApi(useCases, limits)
        }
        return createClient {
            install(ContentNegotiation) { json(ProtocolJson) }
            install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(ProtocolJson) }
        }
    }

    private suspend fun HttpClient.register(name: String = "Desk", registrationToken: String? = null) =
        post("/v1/accounts") {
            contentType(ContentType.Application.Json)
            setBody(CreateAccountRequest(name, PlatformDto.WINDOWS, registrationToken))
        }

    private suspend fun HttpClient.pushFavorite(token: String, key: String) = post("/v1/sync/changes") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(PushChangesRequest(listOf(ChangeDto("favorites", key, false, buildJsonObject { put("title", key) }, 10))))
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

    @Test
    fun registrationRequiresTheConfiguredToken() = testApplication {
        val client = setUp(RegistrationPolicy(RegistrationMode.TOKEN, "invite"))
        assertEquals(HttpStatusCode.Forbidden, client.register().status)
        assertEquals(HttpStatusCode.Forbidden, client.register(registrationToken = "guess").status)
        assertEquals(HttpStatusCode.Created, client.register(registrationToken = "invite").status)
    }

    @Test
    fun registrationIsRateLimitedPerHost() = testApplication {
        val client = setUp(limits = HubApiLimits(registrationsPerHour = 2))
        assertEquals(HttpStatusCode.Created, client.register().status)
        assertEquals(HttpStatusCode.Created, client.register().status)
        val limited = client.register()
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertTrue(limited.body<ProblemDetails>().type.endsWith("rate-limited"))
    }

    @Test
    fun oversizedBodiesAreRefused() = testApplication {
        val client = setUp(limits = HubApiLimits(maxRegistrationBodyBytes = 256, maxSyncBodyBytes = 512))
        assertEquals(HttpStatusCode.PayloadTooLarge, client.register(name = "x".repeat(400)).status)
        val token = client.register().body<CreateAccountResponse>().deviceToken
        assertEquals(HttpStatusCode.PayloadTooLarge, client.pushFavorite(token, "test:" + "k".repeat(600)).status)
        assertEquals(HttpStatusCode.OK, client.pushFavorite(token, "test:1").status)
    }

    @Test
    fun malformedJsonIsABadRequestWithoutInternals() = testApplication {
        val client = setUp()
        val response = client.post("/v1/accounts") {
            contentType(ContentType.Application.Json)
            setBody("{not json")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("The request is malformed", response.body<ProblemDetails>().detail)
    }

    @Test
    fun newerProtocolVersionsAreRefused() = testApplication {
        val client = setUp()
        val refused = client.get("/v1/sync/changes") { header(ProtocolVersion.HEADER, ProtocolVersion.CURRENT + 1) }
        assertEquals(HttpStatusCode.BadRequest, refused.status)
        assertTrue(refused.body<ProblemDetails>().type.endsWith("unsupported-protocol"))
        val accepted = client.get("/v1/sync/changes") { header(ProtocolVersion.HEADER, ProtocolVersion.CURRENT) }
        assertEquals(HttpStatusCode.Unauthorized, accepted.status)
    }

    @Test
    fun devicesCanBeListedAndRevoked() = testApplication {
        val client = setUp()
        val created = client.register().body<CreateAccountResponse>()
        val stranger = client.register("Other").body<CreateAccountResponse>()

        val devices = client.get("/v1/devices") { bearerAuth(created.deviceToken) }.body<DevicesResponse>().devices
        assertEquals(listOf(created.deviceId to true), devices.map { it.id to it.current })

        val foreign = client.delete("/v1/devices/${created.deviceId}") { bearerAuth(stranger.deviceToken) }
        assertEquals(HttpStatusCode.NotFound, foreign.status)

        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/devices/${created.deviceId}") { bearerAuth(created.deviceToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/devices") { bearerAuth(created.deviceToken) }.status)
    }

    @Test
    fun deletingTheAccountRemovesItsDataAndAccess() = testApplication {
        val client = setUp()
        val token = client.register().body<CreateAccountResponse>().deviceToken
        client.pushFavorite(token, "test:1")
        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/account") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/sync/changes") { bearerAuth(token) }.status)
    }

    @Test
    fun revisionEventsReachOnlyTheOwningAccount() = testApplication {
        val client = setUp()
        val mine = client.register().body<CreateAccountResponse>().deviceToken
        val other = client.register("Other").body<CreateAccountResponse>().deviceToken
        client.webSocket("/v1/sync/events", { bearerAuth(mine) }) {
            client.pushFavorite(other, "test:other")
            var pushed = false
            val event = withTimeout(5_000) {
                while (true) {
                    if (!pushed) pushed = client.pushFavorite(mine, "test:mine").status == HttpStatusCode.OK
                    val received = withTimeoutOrNull(200) { receiveDeserialized<RevisionEvent>() }
                    if (received != null) return@withTimeout received
                    pushed = false
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
            assertTrue(event.revision >= 1)
        }
    }
}
