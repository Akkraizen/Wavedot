package space.wavedot.plugin.lrclib

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import space.wavedot.core.contract.LyricsRequest
import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LrclibLyricsProviderTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private val provider = LrclibLyricsProvider(
        HttpClient(MockEngine { request ->
            requests += request
            val title = request.url.parameters["track_name"]
            when (request.url.encodedPath) {
                "/api/get" -> when (title) {
                    "Kukushka" -> respond(
                        """{"id":1,"trackName":"Kukushka","artistName":"Kino","duration":395.0,"instrumental":false,
                           "plainLyrics":"Песен ещё","syncedLyrics":"[00:20.10] Песен ещё\n[00:24.00] ненаписанных"}""",
                        HttpStatusCode.OK,
                        json,
                    )
                    "Interlude" -> respond("""{"id":2,"trackName":"Interlude","artistName":"Kino","instrumental":true}""", HttpStatusCode.OK, json)
                    else -> respond("""{"code":404,"name":"TrackNotFound"}""", HttpStatusCode.NotFound, json)
                }
                "/api/search" -> respond(
                    """[{"id":3,"trackName":"Peremen","artistName":"Kino","duration":500.0,"syncedLyrics":"[00:01.00]Wrong"},
                        {"id":4,"trackName":"Peremen","artistName":"Kino","duration":296.0,"plainLyrics":"Перемен\nтребуют наши сердца"}]""",
                    HttpStatusCode.OK,
                    json,
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }),
        baseUrl = "https://lrclib.test",
    )

    private fun track(title: String, seconds: Int? = 395) = Track(
        id = MediaId(SourceId("yandex-music"), title),
        title = title,
        artists = listOf(ArtistRef(null, "Kino")),
        album = AlbumRef(null, "Zvezda po imeni Solntse"),
        duration = seconds?.seconds,
    )

    @Test
    fun returnsSyncedLyricsFromExactMatch() = runTest {
        val lyrics = assertNotNull(provider.find(LyricsRequest(track("Kukushka"))))
        assertTrue(lyrics.synced)
        assertEquals("ненаписанных", lyrics.lines[1].text)
        val request = requests.single()
        assertEquals("Kino", request.url.parameters["artist_name"])
        assertEquals("395", request.url.parameters["duration"])
        assertEquals("Zvezda po imeni Solntse", request.url.parameters["album_name"])
    }

    @Test
    fun retriesWithCleanTitleThenSearchesByDuration() = runTest {
        val lyrics = assertNotNull(provider.find(LyricsRequest(track("Peremen (Remastered 2019)", seconds = 297))))
        assertFalse(lyrics.synced)
        assertEquals("требуют наши сердца", lyrics.lines[1].text)
        assertEquals(
            listOf("Peremen (Remastered 2019)", "Peremen", "Peremen"),
            requests.map { it.url.parameters["track_name"] },
        )
        assertEquals("/api/search", requests.last().url.encodedPath)
    }

    @Test
    fun marksInstrumentalTracks() = runTest {
        val lyrics = assertNotNull(provider.find(LyricsRequest(track("Interlude"))))
        assertEquals(LrclibLyricsProvider.INSTRUMENTAL, lyrics.lines.single().text)
    }

    @Test
    fun noArtistMeansNoLookup() = runTest {
        assertNull(provider.find(LyricsRequest(track("Kukushka").copy(artists = emptyList()))))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun cleansDecoratedTitles() {
        assertEquals("Peremen", TitleNormalizer.clean("Peremen (Remastered 2019)"))
        assertEquals("Song", TitleNormalizer.clean("Song - Live Version"))
        assertEquals("Song (Part II)", TitleNormalizer.clean("Song (Part II)"))
    }
}
