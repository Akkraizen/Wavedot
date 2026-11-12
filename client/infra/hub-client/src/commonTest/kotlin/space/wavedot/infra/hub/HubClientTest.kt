package space.wavedot.infra.hub

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import space.wavedot.core.common.InMemoryKeyValueStore
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.domain.library.Favorite
import space.wavedot.core.domain.library.FavoriteChange
import space.wavedot.core.domain.sync.SyncRecord
import space.wavedot.core.testing.TestTracks
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.hub.ChangeDto
import space.wavedot.protocol.hub.CreateAccountRequest
import space.wavedot.protocol.hub.CreateAccountResponse
import space.wavedot.protocol.hub.PullChangesResponse
import space.wavedot.protocol.hub.PushChangesRequest
import space.wavedot.protocol.hub.PushChangesResponse
import space.wavedot.protocol.hub.RevisionedChangeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HubClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private var lastPush: PushChangesRequest? = null
    private var lastAuth: String? = null
    private var lastRegistration: CreateAccountRequest? = null
    private val deleted = mutableListOf<String>()
    private val track = TestTracks.track("42")

    private val engine = MockEngine { request ->
        lastAuth = request.headers[HttpHeaders.Authorization]
        when {
            request.url.encodedPath == "/v1/accounts" -> {
                lastRegistration = ProtocolJson.decodeFromString(request.body.toByteArray().decodeToString())
                respond(ProtocolJson.encodeToString(CreateAccountResponse("acc", "dev", "tok")), HttpStatusCode.Created, jsonHeaders)
            }
            request.method == HttpMethod.Delete -> {
                deleted += request.url.encodedPath
                respond("", HttpStatusCode.NoContent)
            }
            request.url.encodedPath == "/v1/sync/changes" && request.method == HttpMethod.Post -> {
                lastPush = ProtocolJson.decodeFromString(request.body.toByteArray().decodeToString())
                respond(ProtocolJson.encodeToString(PushChangesResponse(7)), HttpStatusCode.OK, jsonHeaders)
            }
            request.url.encodedPath == "/v1/sync/changes" -> {
                val change = ChangeDto("favorites", track.id.toString(), deleted = true, updatedAt = 9)
                val unknown = ChangeDto("future-collection", "x", deleted = false, updatedAt = 9)
                val malformedKey = ChangeDto("favorites", "no-separator", deleted = true, updatedAt = 9)
                val malformedPayload = ChangeDto("favorites", "test:7", deleted = false, payload = JsonPrimitive("oops"), updatedAt = 9)
                val body = PullChangesResponse(
                    listOf(
                        RevisionedChangeDto(6, "other", malformedKey),
                        RevisionedChangeDto(7, "other", malformedPayload),
                        RevisionedChangeDto(8, "other", change),
                        RevisionedChangeDto(9, "other", unknown),
                    ),
                    revision = 9,
                    hasMore = false,
                )
                respond(ProtocolJson.encodeToString(body), HttpStatusCode.OK, jsonHeaders)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private val plainStore = InMemoryKeyValueStore()
    private val secretStore = InMemoryKeyValueStore()
    private val settings = HubSettings(plainStore, secretStore)
    private val client = HubClient(HttpClient(engine), settings)

    @Test
    fun registersAndStoresConnection() = runTest {
        assertFalse(client.isConfigured())
        client.createAccount("https://hub.test/", "Desk", PlatformDto.WINDOWS)
        assertTrue(client.isConfigured())
        assertEquals("https://hub.test", settings.connection()!!.baseUrl)
    }

    @Test
    fun pushesFavoritesWithBearerToken() = runTest {
        client.createAccount("https://hub.test", "Desk", PlatformDto.WINDOWS)
        val record = SyncRecord.Favorite(FavoriteChange(track.id, Favorite(track, 3), 3))
        assertEquals(7L, client.push(listOf(record)))
        assertEquals("Bearer tok", lastAuth)
        val sent = lastPush!!.changes.single()
        assertEquals("favorites", sent.collection)
        assertEquals("test:42", sent.key)
        assertFalse(sent.deleted)
    }

    @Test
    fun pullsAndIgnoresUnknownCollectionsAndMalformedRecords() = runTest {
        client.createAccount("https://hub.test", "Desk", PlatformDto.WINDOWS)
        val batch = client.pull(0)
        assertEquals(9L, batch.revision)
        val change = (batch.records.single() as SyncRecord.Favorite).change
        assertEquals(track.id, change.id)
        assertTrue(change.deleted)
    }

    @Test
    fun keepsTheDeviceTokenInTheSecretStore() = runTest {
        client.createAccount("https://hub.test", "Desk", PlatformDto.WINDOWS, registrationToken = " invite ")
        assertEquals(" invite ", lastRegistration!!.registrationToken)
        assertEquals("tok", secretStore.get("hub.token"))
        assertNull(plainStore.get("hub.token"))
    }

    @Test
    fun movesALegacyTokenOutOfPlainSettings() = runTest {
        plainStore.set("hub.url", "https://hub.test")
        plainStore.set("hub.token", "legacy")
        assertEquals("legacy", settings.connection()!!.deviceToken)
        assertEquals("legacy", secretStore.get("hub.token"))
        assertNull(plainStore.get("hub.token"))
    }

    @Test
    fun refusesPlainHttpHubs() = runTest {
        val error = assertFailsWith<WavedotException> { client.createAccount("http://hub.test", "Desk", PlatformDto.WINDOWS) }
        assertIs<WavedotError.Invalid>(error.error)
        assertFalse(client.isConfigured())
    }

    @Test
    fun disconnectRevokesTheDeviceAndForgetsTheToken() = runTest {
        client.createAccount("https://hub.test", "Desk", PlatformDto.WINDOWS)
        client.disconnect()
        assertEquals(listOf("/v1/devices/dev"), deleted)
        assertEquals("Bearer tok", lastAuth)
        assertFalse(client.isConfigured())
        assertNull(secretStore.get("hub.token"))
    }

    @Test
    fun deletesTheAccountOnRequest() = runTest {
        client.createAccount("https://hub.test", "Desk", PlatformDto.WINDOWS)
        client.deleteAccount()
        assertEquals(listOf("/v1/account"), deleted)
        assertFalse(client.isConfigured())
    }
}
