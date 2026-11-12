package space.wavedot.plugin.audius

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.EntityKind
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.core.testing.MediaSourceContractTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

internal class FakeAudius {
    val requests = mutableListOf<HttpRequestData>()

    private val user = """{"id":"u1","name":"Lofi Girl","handle":"lofigirl",
        "profile_picture":{"150x150":"https://img/u1-150.jpg","480x480":"https://img/u1-480.jpg"}}"""

    private fun track(id: String, streamable: Boolean = true) = """
        {"id":"$id","title":" Track $id ","duration":125,"is_streamable":$streamable,"user":$user,
         "artwork":{"150x150":"https://img/$id-150.jpg","480x480":"https://img/$id-480.jpg","mirrors":["https://m"]},
         "genre":"Lo-Fi","play_count":10}
    """.trimIndent()

    val client = HttpClient(MockEngine { request ->
        requests += request
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        fun data(body: String) = respond("""{"data":$body}""", HttpStatusCode.OK, json)
        val path = request.url.encodedPath
        val offset = request.url.parameters["offset"]?.toInt() ?: 0
        val limit = request.url.parameters["limit"]?.toInt() ?: 10
        when {
            path == "/v1/tracks/search" -> {
                val all = listOf(track("t1"), track("t2", streamable = false), track("t3"))
                data(all.drop(offset).take(limit).joinToString(",", "[", "]"))
            }
            path == "/v1/tracks/trending" -> data("[${track("t3")},${track("t2", streamable = false)},${track("t1")}]")
            path == "/v1/users/search" -> data("[$user]")
            path == "/v1/playlists/search" ->
                data("""[{"id":"p1","playlist_name":"Beats to relax","track_count":2,"user":$user,"artwork":{"480x480":"https://img/p1.jpg"}}]""")
            path == "/v1/playlists/p1/tracks" -> data("[${track("t1")},${track("t3")}]")
            path == "/v1/users/u1/tracks" -> data("[${track("t3")}]")
            path.startsWith("/v1/tracks/t") -> data(track(path.substringAfterLast('/')))
            else -> respond("""{"code":404,"error":"Not Found"}""", HttpStatusCode.NotFound, json)
        }
    })

    val api = AudiusApi(client, "https://audius.test/v1")

    fun source(): MediaSource = AudiusSource(api)
}

class AudiusSourceContractTest : MediaSourceContractTest() {
    override suspend fun createSource(): MediaSource = FakeAudius().source()

    override val knownTrackId: MediaId = MediaId(AudiusSource.SOURCE_ID, "t1")

    override val searchText: String = "lofi"
}

class AudiusSourceTest {
    private val server = FakeAudius()
    private val source = server.source()

    @Test
    fun mapsTrackMetadata() = runTest {
        val track = assertNotNull(source.catalog!!.tracks.get(MediaId(AudiusSource.SOURCE_ID, "t1")))
        assertEquals("Track t1", track.title)
        assertEquals("Lofi Girl", track.artistLine)
        assertEquals(125.seconds, track.duration)
        assertEquals("https://img/t1-480.jpg", track.artwork?.url)
        assertEquals("Wavedot", server.requests.last().url.parameters["app_name"])
    }

    @Test
    fun searchHidesUnstreamableTracksButKeepsPaging() = runTest {
        val first = source.search!!.tracks.page(SearchQuery("lofi"), limit = 2)
        assertEquals(listOf("t1"), first.items.map { it.id.key })
        val second = source.search!!.tracks.page(SearchQuery("lofi"), first.next, limit = 2)
        assertEquals(listOf("t3"), second.items.map { it.id.key })
        assertNull(second.next)
    }

    @Test
    fun playlistsActAsAlbumsAndUsersAsArtists() = runTest {
        val query = SearchQuery("lofi", setOf(EntityKind.ALBUM, EntityKind.ARTIST))
        val album = source.search!!.albums!!.page(query).items.single()
        assertEquals("Beats to relax", album.title)
        assertEquals(2, album.trackCount)
        val tracks = source.catalog!!.albumTracks!!.page(album.id)
        assertEquals(listOf("t1", "t3"), tracks.items.map { it.id.key })

        val artist = source.search!!.artists!!.page(query).items.single()
        assertEquals("https://img/u1-480.jpg", artist.artwork?.url)
        assertEquals(listOf("t3"), source.catalog!!.artistTracks!!.page(artist.id).items.map { it.id.key })
    }

    @Test
    fun unknownTrackIsNull() = runTest {
        assertNull(source.catalog!!.tracks.get(MediaId(AudiusSource.SOURCE_ID, "missing")))
    }

    @Test
    fun streamsThroughRedirectingEndpoint() = runTest {
        val stream = assertIs<StreamSource.Remote>(
            source.streaming!!.resolve(MediaId(AudiusSource.SOURCE_ID, "t1"), AudioQuality.HIGH),
        )
        assertEquals("https://audius.test/v1/tracks/t1/stream?app_name=Wavedot", stream.url)
    }
}
