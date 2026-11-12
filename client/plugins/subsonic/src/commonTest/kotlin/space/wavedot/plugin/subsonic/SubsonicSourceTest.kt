package space.wavedot.plugin.subsonic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.EntityKind
import space.wavedot.core.model.ArtworkReference
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.core.testing.MediaSourceContractTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal class FakeSubsonic(private val password: String = "sesame") {
    val requests = mutableListOf<HttpRequestData>()
    val starred = mutableSetOf("s1")

    private fun song(id: String) =
        """{"id":"$id","title":"Song $id","artist":"Kino","artistId":"ar1","album":"Gruppa krovi","albumId":"al1",
           "duration":215,"coverArt":"al-1","contentType":"audio/flac","suffix":"flac"}"""

    private fun ok(body: String = "") =
        """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome"${if (body.isEmpty()) "" else ",$body"}}}"""

    private fun failed(code: Int, message: String) =
        """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":$code,"message":"$message"}}}"""

    val client = HttpClient(MockEngine { request ->
        requests += request
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val params = request.url.parameters
        val body = when {
            params["t"] != md5Hex(password + params["s"]) -> failed(40, "Wrong username or password")
            else -> when (request.url.encodedPath.substringAfterLast('/')) {
                "ping.view" -> ok()
                "search3.view" -> {
                    val songs = (1..3).map { "s$it" }
                        .drop(params["songOffset"]!!.toInt())
                        .take(params["songCount"]!!.toInt())
                        .joinToString(",") { song(it) }
                    val albums = if (params["albumCount"] == "0") "" else
                        """{"id":"al1","name":"Gruppa krovi","artist":"Kino","artistId":"ar1","coverArt":"al-1","songCount":2,"year":1988}"""
                    val artists = if (params["artistCount"] == "0") "" else """{"id":"ar1","name":"Kino","coverArt":"ar-1"}"""
                    ok(""""searchResult3":{"song":[$songs],"album":[$albums],"artist":[$artists]}""")
                }
                "getSong.view" -> if (params["id"]!!.startsWith("s")) ok(""""song":${song(params["id"]!!)}""") else failed(70, "Song not found")
                "getAlbum.view" -> ok(""""album":{"id":"al1","name":"Gruppa krovi","song":[${song("s1")},${song("s2")}]}""")
                "getArtist.view" -> ok(""""artist":{"id":"ar1","name":"Kino","album":[{"id":"al1","name":"Gruppa krovi"}]}""")
                "getStarred2.view" -> ok(""""starred2":{"song":[${starred.joinToString(",") { song(it) }}]}""")
                "star.view" -> ok().also { starred += params["id"]!! }
                "unstar.view" -> ok().also { starred -= params["id"]!! }
                else -> failed(0, "Unknown method")
            }
        }
        respond(body, HttpStatusCode.OK, json)
    })

    fun api(password: String = this.password) =
        SubsonicApi(client, SubsonicConfig("https://music.test/", "igor", password), salt = "c19b2d")

    fun source(): MediaSource = SubsonicSource(api(), "Subsonic · music.test")
}

class SubsonicSourceContractTest : MediaSourceContractTest() {
    override suspend fun createSource(): MediaSource = FakeSubsonic().source()

    override val knownTrackId: MediaId = MediaId(SubsonicSource.SOURCE_ID, "s1")

    override val searchText: String = "song"
}

class SubsonicSourceTest {
    private val server = FakeSubsonic()
    private val source = server.source()

    @Test
    fun signsRequestsWithSaltedToken() = runTest {
        source.catalog!!.tracks.get(MediaId(SubsonicSource.SOURCE_ID, "s1"))
        val params = server.requests.single().url.parameters
        assertEquals("igor", params["u"])
        assertEquals("c19b2d", params["s"])
        assertEquals(md5Hex("sesamec19b2d"), params["t"])
        assertEquals("json", params["f"])
        assertEquals("https://music.test/rest/getSong.view", server.requests.single().url.toString().substringBefore('?'))
    }

    @Test
    fun md5MatchesKnownDigest() {
        assertEquals("fdc0ffb8619030c0c53bc7c0694ec253", md5Hex("sesamec19a2b"))
    }

    @Test
    fun mapsSongs() = runTest {
        val track = assertNotNull(source.catalog!!.tracks.get(MediaId(SubsonicSource.SOURCE_ID, "s1")))
        assertEquals("Song s1", track.title)
        assertEquals("Kino", track.artistLine)
        assertEquals(MediaId(SubsonicSource.SOURCE_ID, "album/al1"), track.album?.id)
        assertEquals(215.seconds, track.duration)
        val reference = assertNotNull(track.artwork).url
        assertEquals(ArtworkReference.of(SubsonicSource.SOURCE_ID, "al-1"), reference)
        assertFalse("sesame" in reference || "t=" in reference)
        val cover = Url(assertNotNull(source.artwork!!.resolve("al-1")))
        assertEquals("/rest/getCoverArt.view", cover.encodedPath)
        assertEquals("al-1", cover.parameters["id"])
        assertNull(source.catalog!!.tracks.get(MediaId(SubsonicSource.SOURCE_ID, "missing")))
    }

    @Test
    fun searchPagesByOffsetAndKind() = runTest {
        val first = source.search!!.tracks.page(SearchQuery("song"), limit = 2)
        assertEquals(listOf("s1", "s2"), first.items.map { it.id.key })
        val second = source.search!!.tracks.page(SearchQuery("song"), first.next, limit = 2)
        assertEquals(listOf("s3"), second.items.map { it.id.key })
        assertNull(second.next)

        val albums = source.search!!.albums!!.page(SearchQuery("kino", setOf(EntityKind.ALBUM))).items
        assertEquals(listOf("Gruppa krovi"), albums.map { it.title })
        assertEquals(1988, albums.single().year)
        val artists = source.search!!.artists!!.page(SearchQuery("kino", setOf(EntityKind.ARTIST))).items
        assertEquals(listOf("Kino"), artists.map { it.name })
    }

    @Test
    fun loadsAlbumAndArtistTracks() = runTest {
        val album = source.catalog!!.albumTracks!!.page(MediaId(SubsonicSource.SOURCE_ID, "album/al1"))
        assertEquals(listOf("s1", "s2"), album.items.map { it.id.key })
        val artist = source.catalog!!.artistTracks!!.page(MediaId(SubsonicSource.SOURCE_ID, "artist/ar1"))
        assertEquals(listOf("s1", "s2"), artist.items.map { it.id.key })
        assertNull(artist.next)
    }

    @Test
    fun streamsWithBitrateCapForLossyQualities() = runTest {
        val lossless = assertIs<StreamSource.Remote>(source.streaming!!.resolve(MediaId(SubsonicSource.SOURCE_ID, "s1"), AudioQuality.LOSSLESS))
        val url = Url(lossless.url)
        assertEquals("/rest/stream.view", url.encodedPath)
        assertEquals("s1", url.parameters["id"])
        assertNull(url.parameters["maxBitRate"])
        val low = assertIs<StreamSource.Remote>(source.streaming!!.resolve(MediaId(SubsonicSource.SOURCE_ID, "s1"), AudioQuality.LOW))
        assertEquals("128", Url(low.url).parameters["maxBitRate"])
    }

    @Test
    fun starsAndUnstarsLikes() = runTest {
        val library = source.library!!
        library.like(MediaId(SubsonicSource.SOURCE_ID, "s2"))
        assertEquals(listOf("s1", "s2"), library.likedTracks.page(Unit).items.map { it.id.key })
        library.unlike(MediaId(SubsonicSource.SOURCE_ID, "s1"))
        assertEquals(listOf("s2"), library.likedTracks.page(Unit).items.map { it.id.key })
    }

    @Test
    fun wrongPasswordIsUnauthorized() = runTest {
        val error = assertFailsWith<WavedotException> { server.api(password = "nope").ping() }
        assertIs<WavedotError.Unauthorized>(error.error)
    }

    @Test
    fun displayNameShowsHost() {
        assertEquals("Subsonic · music.example.com", SubsonicPlugin.displayName("https://music.example.com/"))
        assertTrue(SubsonicPlugin.displayName("not a url").startsWith("Subsonic"))
    }
}
