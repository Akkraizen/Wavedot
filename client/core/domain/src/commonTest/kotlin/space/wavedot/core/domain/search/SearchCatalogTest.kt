package space.wavedot.core.domain.search

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.WavedotException
import space.wavedot.core.model.EntityKind
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.SourceId
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchCatalogTest {
    private val good = FakeMediaSource(SourceId("good"), tracks = TestTracks.tracks(30, SourceId("good")))
    private val broken = FakeMediaSource(SourceId("broken"), searchFailure = IllegalStateException("down"))
    private val search = SearchCatalog(TestRegistry(good, broken))

    private val discography = FakeMediaSource(
        tracks = listOf(
            TestTracks.albumTrack("1", album = "Gruppa krovi", artist = "Kino"),
            TestTracks.albumTrack("2", album = "Gruppa krovi", artist = "Kino"),
            TestTracks.albumTrack("3", album = "Zvezda", artist = "Kino"),
            TestTracks.albumTrack("4", album = "Razluka", artist = "Nautilus"),
        ),
    )

    @Test
    fun aggregatesTrackResultsPerSourceAndIsolatesFailures() = runTest {
        val results = search("track", SearchScope.TRACKS, limit = 10)
        assertEquals(2, results.size)
        val ok = results.first { it.source.id == SourceId("good") }
        assertEquals(10, ok.tracks.items.size)
        assertNotNull(ok.tracks.next)
        assertNull(ok.error)
        assertNotNull(results.first { it.source.id == SourceId("broken") }.error)
    }

    @Test
    fun searchesOnlySelectedSources() = runTest {
        val results = search("track", SearchScope.TRACKS, sources = setOf(SourceId("good")))
        assertEquals(listOf(SourceId("good")), results.map { it.source.id })
    }

    @Test
    fun listsSearchableSources() = runTest {
        val local = FakeMediaSource(SourceId("plain"), searchable = false)
        val sources = SearchCatalog(TestRegistry(good, local, broken)).sources.first()
        assertEquals(listOf(SourceId("good"), SourceId("broken")), sources.map { it.id })
    }

    @Test
    fun blankQueryReturnsNothing() = runTest {
        assertTrue(search("   ").isEmpty())
    }

    @Test
    fun scopeSelectsEntityKinds() = runTest {
        val catalog = SearchCatalog(TestRegistry(discography))
        val albums = catalog("gruppa", SearchScope.ALBUMS).single()
        assertEquals(listOf("Gruppa krovi"), albums.albums.items.map { it.title })
        assertTrue(albums.tracks.items.isEmpty())
        assertTrue(albums.artists.items.isEmpty())

        val everything = catalog("kino", SearchScope.ALL).single()
        assertEquals(listOf("Kino"), everything.artists.items.map { it.name })
    }

    @Test
    fun loadsAlbumAndArtistTracks() = runTest {
        val load = LoadCollection(TestRegistry(discography))
        assertEquals(listOf("1", "2"), load(MediaId(TestTracks.SOURCE, "album/Gruppa krovi"), EntityKind.ALBUM).map { it.id.key })
        assertEquals(listOf("1", "2", "3"), load(MediaId(TestTracks.SOURCE, "artist/Kino"), EntityKind.ARTIST).map { it.id.key })
    }

    @Test
    fun loadingTracksOfATrackIsUnsupported() = runTest {
        val load = LoadCollection(TestRegistry(discography))
        assertFailsWith<WavedotException> { load(MediaId(TestTracks.SOURCE, "1"), EntityKind.TRACK) }
    }
}
