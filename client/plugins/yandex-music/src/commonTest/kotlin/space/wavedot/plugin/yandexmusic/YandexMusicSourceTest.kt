package space.wavedot.plugin.yandexmusic

import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.core.testing.MediaSourceContractTest
import space.wavedot.plugin.yandexmusic.api.ClientProfile
import space.wavedot.plugin.yandexmusic.api.YandexMusicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private fun sourceFor(server: FakeYandexServer, token: String = "secret"): MediaSource =
    YandexMusicSource(YandexMusicApi(server.client, token, ClientProfile.ANDROID, { 1_700_000_000_000L }), server.client)

class YandexMusicSourceContractTest : MediaSourceContractTest() {
    override suspend fun createSource(): MediaSource = sourceFor(FakeYandexServer())

    override val knownTrackId: MediaId = MediaId(YandexMusicSource.SOURCE_ID, "42")

    override val searchText: String = "sigaret"
}

class YandexMusicSourceTest {
    private val server = FakeYandexServer()
    private val source = sourceFor(server)

    @Test
    fun mapsTrackMetadata() = runTest {
        val track = assertNotNull(source.catalog!!.tracks.get(MediaId(YandexMusicSource.SOURCE_ID, "42")))
        assertEquals("Song 42 (Remastered)", track.title)
        assertEquals("Kino", track.artistLine)
        assertEquals("Gruppa krovi", track.album?.title)
        assertEquals(215.seconds, track.duration)
        assertEquals("https://avatars.yandex.net/get-music-content/1/abc/400x400", track.artwork?.url)
    }

    @Test
    fun paginatesSearchByPageNumber() = runTest {
        val first = source.search!!.tracks.page(SearchQuery("sigaret"))
        assertEquals(listOf("1", "2"), first.items.map { it.id.key })
        val second = source.search!!.tracks.page(SearchQuery("sigaret"), first.next)
        assertEquals(listOf("3", "4"), second.items.map { it.id.key })
        val third = source.search!!.tracks.page(SearchQuery("sigaret"), second.next)
        assertNull(third.next)
    }

    @Test
    fun resolvesEncryptedStreamWithSignedRequest() = runTest {
        val stream = source.streaming!!.resolve(MediaId(YandexMusicSource.SOURCE_ID, "42"), AudioQuality.LOSSLESS)
        val custom = assertIs<StreamSource.Custom>(stream)
        assertEquals("audio/mp4", custom.mimeType)
        val request = server.requests.last { it.url.encodedPath == "/get-file-info" }
        assertEquals("1700000000", request.url.parameters["ts"])
        assertEquals("lossless", request.url.parameters["quality"])
        assertEquals("encraw", request.url.parameters["transports"])
        assertEquals(ClientProfile.ANDROID.clientHeader, request.headers[YandexMusicApi.CLIENT_HEADER])
        assertTrue(request.url.parameters["sign"]!!.isNotBlank())
    }

    @Test
    fun pagesLikedTracksAndStripsAlbumSuffix() = runTest {
        val library = source.library!!
        val first = library.likedTracks.page(Unit, limit = 2)
        assertEquals(listOf("10", "11"), first.items.map { it.id.key })
        val second = library.likedTracks.page(Unit, first.next, limit = 2)
        assertEquals(listOf("12"), second.items.map { it.id.key })
        assertNull(second.next)
    }

    @Test
    fun likeAndUnlikeUseAccountUid() = runTest {
        source.library!!.like(MediaId(YandexMusicSource.SOURCE_ID, "5"))
        source.library!!.unlike(MediaId(YandexMusicSource.SOURCE_ID, "5"))
        assertEquals(listOf("add-multiple:track-ids=5", "remove:track-ids=5"), server.likedChanges)
    }

    @Test
    fun invalidTokenSurfacesAsUnauthorized() = runTest {
        val unauthorized = sourceFor(server, token = "wrong")
        val error = assertFailsWith<WavedotException> { unauthorized.catalog!!.tracks.get(MediaId(YandexMusicSource.SOURCE_ID, "1")) }
        assertIs<WavedotError.Unauthorized>(error.error)
    }
}
