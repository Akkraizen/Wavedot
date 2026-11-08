package space.wavedot.plugin.yandexmusic

import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.EntityKind
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
    fun searchesAlbumsAndArtists() = runTest {
        val album = source.search!!.albums!!.page(SearchQuery("gruppa", setOf(EntityKind.ALBUM))).items.single()
        assertEquals(MediaId(YandexMusicSource.SOURCE_ID, "album/99"), album.id)
        assertEquals("Kino", album.artists.single().name)
        assertEquals(1988, album.year)
        assertEquals("https://avatars.yandex.net/get-music-content/2/def/400x400", album.artwork?.url)

        val artist = source.search!!.artists!!.page(SearchQuery("kino", setOf(EntityKind.ARTIST))).items.single()
        assertEquals(MediaId(YandexMusicSource.SOURCE_ID, "artist/7"), artist.id)
        assertEquals("https://avatars.yandex.net/get-music-content/3/ghi/400x400", artist.artwork?.url)
    }

    @Test
    fun loadsAlbumAndArtistTracks() = runTest {
        val catalog = source.catalog!!
        val album = catalog.albumTracks!!.page(MediaId(YandexMusicSource.SOURCE_ID, "album/99"))
        assertEquals(listOf("101", "102", "103"), album.items.map { it.id.key })
        assertNull(album.next)

        val first = catalog.artistTracks!!.page(MediaId(YandexMusicSource.SOURCE_ID, "artist/7"), limit = 2)
        assertEquals(listOf("201", "202"), first.items.map { it.id.key })
        val second = catalog.artistTracks!!.page(MediaId(YandexMusicSource.SOURCE_ID, "artist/7"), first.next, limit = 2)
        assertEquals(listOf("203"), second.items.map { it.id.key })
        assertNull(second.next)
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
