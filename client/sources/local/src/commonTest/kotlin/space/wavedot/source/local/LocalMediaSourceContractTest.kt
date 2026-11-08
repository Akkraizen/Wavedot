package space.wavedot.source.local

import kotlinx.coroutines.test.runTest
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.core.testing.MediaSourceContractTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val sampleTracks = listOf(
    LocalTrack(Track(LocalMediaSource.idFor("/music/a.flac"), "Aurora", listOf(ArtistRef(null, "Nord"))), "/music/a.flac", "audio/flac"),
    LocalTrack(Track(LocalMediaSource.idFor("/music/b.mp3"), "Boreal", listOf(ArtistRef(null, "Sud"))), "/music/b.mp3", "audio/mpeg"),
)

private class StaticScanner(private val tracks: List<LocalTrack>) : LocalTrackScanner {
    override suspend fun scan(): List<LocalTrack> = tracks
}

class LocalMediaSourceContractTest : MediaSourceContractTest() {
    override suspend fun createSource(): MediaSource = LocalMediaSource(StaticScanner(sampleTracks)).apply { refresh() }

    override val knownTrackId: MediaId = LocalMediaSource.idFor("/music/a.flac")

    override val searchText: String = "nord"
}

class LocalMediaSourceTest {
    @Test
    fun resolvesLocalFileStream() = runTest {
        val source = LocalMediaSource(StaticScanner(sampleTracks)).apply { refresh() }
        val stream = source.streaming.resolve(LocalMediaSource.idFor("/music/b.mp3"), AudioQuality.LOSSLESS)
        assertEquals(StreamSource.LocalFile("/music/b.mp3", "audio/mpeg"), stream)
    }

    @Test
    fun groupsAlbumsAndArtists() = runTest {
        val tracks = listOf(
            LocalTrack(Track(LocalMediaSource.idFor("/m/1.flac"), "One", listOf(ArtistRef(null, "Kino")), AlbumRef(null, "Gruppa krovi")), "/m/1.flac"),
            LocalTrack(Track(LocalMediaSource.idFor("/m/2.flac"), "Two", listOf(ArtistRef(null, "Kino")), AlbumRef(null, "Gruppa krovi")), "/m/2.flac"),
            LocalTrack(Track(LocalMediaSource.idFor("/m/3.flac"), "Three", listOf(ArtistRef(null, "Kino")), AlbumRef(null, "Zvezda")), "/m/3.flac"),
        )
        val source = LocalMediaSource(StaticScanner(tracks)).apply { refresh() }
        val album = source.search.albums!!.page(SearchQuery("gruppa")).items.single()
        assertEquals(2, album.trackCount)
        assertEquals(listOf("One", "Two"), source.catalog.albumTracks!!.page(album.id).items.map { it.title })
        val artist = source.search.artists!!.page(SearchQuery("kino")).items.single()
        assertEquals(3, source.catalog.artistTracks!!.page(artist.id).items.size)
    }

    @Test
    fun windowsPathsSurviveMediaIdRoundTrip() {
        val id = LocalMediaSource.idFor("C:\\Music\\song.flac")
        assertEquals(id, MediaId.parse(id.toString()))
    }
}
