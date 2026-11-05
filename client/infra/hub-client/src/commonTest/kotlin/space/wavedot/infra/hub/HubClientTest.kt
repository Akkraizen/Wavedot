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
import space.wavedot.core.common.InMemoryKeyValueStore
import space.wavedot.core.domain.library.Favorite
import space.wavedot.core.domain.library.FavoriteChange
import space.wavedot.core.domain.sync.SyncRecord
import space.wavedot.core.testing.TestTracks
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.hub.ChangeDto
import space.wavedot.protocol.hub.CreateAccountResponse
import space.wavedot.protocol.hub.PullChangesResponse
import space.wavedot.protocol.hub.PushChangesRequest
import space.wavedot.protocol.hub.PushChangesResponse
import space.wavedot.protocol.hub.RevisionedChangeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private var lastPush: PushChangesRequest? = null
    private var lastAuth: String? = null
    private val track = TestTracks.track("42")

    private val engine = MockEngine { request ->
        lastAuth = request.headers[HttpHeaders.Authorization]
        when {
            request.url.encodedPath == "/v1/accounts" ->
                respond(ProtocolJson.encodeToString(CreateAccountResponse("acc", "dev", "tok")), HttpStatusCode.Created, jsonHeaders)
            request.url.encodedPath == "/v1/sync/changes" && request.method == HttpMethod.Post -> {
                lastPush = ProtocolJson.decodeFromString(request.body.toByteArray().decodeToString())
                respond(ProtocolJson.encodeToString(PushChangesResponse(7)), HttpStatusCode.OK, jsonHeaders)
            }
            request.url.encodedPath == "/v1/sync/changes" -> {
                val change = ChangeDto("favorites", track.id.toString(), deleted = true, updatedAt = 9)
                val unknown = ChangeDto("future-collection", "x", deleted = false, updatedAt = 9)
                val body = PullChangesResponse(
                    listOf(RevisionedChangeDto(8, "other", change), RevisionedChangeDto(9, "other", unknown)),
                    revision = 9,
                    hasMore = false,
                )
                respond(ProtocolJson.encodeToString(body), HttpStatusCode.OK, jsonHeaders)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private val settings = HubSettings(InMemoryKeyValueStore())
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
    fun pullsAndIgnoresUnknownCollections() = runTest {
        client.createAccount("https://hub.test", "Desk", PlatformDto.WINDOWS)
        val batch = client.pull(0)
        assertEquals(9L, batch.revision)
        val change = (batch.records.single() as SyncRecord.Favorite).change
        assertEquals(track.id, change.id)
        assertTrue(change.deleted)
    }
}
