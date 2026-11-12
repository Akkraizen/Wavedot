package space.wavedot.plugin.radiobrowser

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
import space.wavedot.core.contract.SourceKind
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.core.testing.MediaSourceContractTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class FakeRadioBrowser {
    val requests = mutableListOf<HttpRequestData>()
    var clickOk = true

    private fun station(uuid: String, name: String) = """
        {"stationuuid":"$uuid","name":"  $name ","url":"http://fallback/$uuid","url_resolved":"http://stream/$uuid",
         "favicon":"https://img/$uuid.png","tags":"jazz,smooth jazz,lounge","country":"Germany","codec":"MP3","bitrate":128,
         "hls":0,"votes":3}
    """.trimIndent()

    val client = HttpClient(MockEngine { request ->
        requests += request
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val path = request.url.encodedPath
        when {
            path == "/json/stations/search" && request.url.parameters["name"] == null -> {
                val country = request.url.parameters["countrycode"] ?: "ALL"
                respond("[${station("$country-1", "Top $country")}]", HttpStatusCode.OK, json)
            }
            path == "/json/stations/search" -> {
                val offset = request.url.parameters["offset"]!!.toInt()
                val limit = request.url.parameters["limit"]!!.toInt()
                val all = (1..3).map { station("s$it", "Jazz $it") }
                respond(all.drop(offset).take(limit).joinToString(",", "[", "]"), HttpStatusCode.OK, json)
            }
            path == "/json/stations/byuuid" -> {
                val ids = request.url.parameters["uuids"]!!.split(',').filter { it.startsWith("s") }
                respond(ids.joinToString(",", "[", "]") { station(it, "Jazz ${it.drop(1)}") }, HttpStatusCode.OK, json)
            }
            path.startsWith("/json/url/") -> {
                val uuid = path.substringAfterLast('/')
                respond("""{"ok":$clickOk,"stationuuid":"$uuid","url":"http://click/$uuid"}""", HttpStatusCode.OK, json)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    })

    val api = RadioBrowserApi(client, "https://rb.test")

    fun source(): MediaSource = RadioBrowserSource(api)
}

class RadioBrowserSourceContractTest : MediaSourceContractTest() {
    override suspend fun createSource(): MediaSource = FakeRadioBrowser().source()

    override val knownTrackId: MediaId = MediaId(RadioBrowserSource.SOURCE_ID, "s1")

    override val searchText: String = "jazz"
}

class RadioBrowserSourceTest {
    private val server = FakeRadioBrowser()
    private val source = server.source()

    @Test
    fun mapsStationsToLiveTracks() = runTest {
        val track = assertNotNull(source.catalog!!.tracks.get(MediaId(RadioBrowserSource.SOURCE_ID, "s2")))
        assertEquals("Jazz 2", track.title)
        assertEquals("Germany · jazz, smooth jazz · MP3 128 kbps", track.artistLine)
        assertEquals("https://img/s2.png", track.artwork?.url)
        assertNull(track.duration)
        assertEquals(SourceKind.RADIO, source.descriptor.kind)
    }

    @Test
    fun paginatesSearchByOffset() = runTest {
        val first = source.search!!.tracks.page(SearchQuery("jazz"), limit = 2)
        assertEquals(listOf("s1", "s2"), first.items.map { it.id.key })
        val second = source.search!!.tracks.page(SearchQuery("jazz"), first.next, limit = 2)
        assertEquals(listOf("s3"), second.items.map { it.id.key })
        assertNull(second.next)
        assertEquals(RadioBrowserApi.USER_AGENT, server.requests.first().headers[HttpHeaders.UserAgent])
    }

    @Test
    fun resolvesThroughClickEndpoint() = runTest {
        val stream = source.streaming!!.resolve(MediaId(RadioBrowserSource.SOURCE_ID, "s1"), AudioQuality.NORMAL)
        assertEquals("http://click/s1", assertIs<StreamSource.Remote>(stream).url)
    }

    @Test
    fun fallsBackToStationUrlWhenClickFails() = runTest {
        server.clickOk = false
        val stream = assertIs<StreamSource.Remote>(
            source.streaming!!.resolve(MediaId(RadioBrowserSource.SOURCE_ID, "s1"), AudioQuality.NORMAL),
        )
        assertEquals("http://stream/s1", stream.url)
        assertEquals("audio/mpeg", stream.mimeType)
    }
}
