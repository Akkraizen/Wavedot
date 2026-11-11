package space.wavedot.presentation.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.domain.player.FixedPlaybackPreferences
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.domain.player.ResolveStream
import space.wavedot.core.domain.search.LoadCollection
import space.wavedot.core.domain.search.SearchCatalog
import space.wavedot.core.domain.search.SearchScope
import space.wavedot.core.model.SourceId
import space.wavedot.core.testing.FakeAudioEngine
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.InMemoryFavoritesRepository
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val source = FakeMediaSource(
        tracks = TestTracks.tracks(3) + listOf(
            TestTracks.albumTrack("a1", album = "Gruppa krovi", artist = "Kino"),
            TestTracks.albumTrack("a2", album = "Gruppa krovi", artist = "Kino"),
        ),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val radio = FakeMediaSource(SourceId("radio"), tracks = TestTracks.tracks(2, SourceId("radio")))

    private fun TestScope.create(vararg extra: FakeMediaSource): Pair<SearchViewModel, PlayerController> {
        val registry = TestRegistry(source, *extra)
        val favorites = InMemoryFavoritesRepository()
        val controller = PlayerController(FakeAudioEngine(), ResolveStream(registry), FixedPlaybackPreferences(), backgroundScope)
        val viewModel = SearchViewModel(
            SearchCatalog(registry),
            LoadCollection(registry),
            favorites,
            controller,
            ToggleFavorite(favorites, registry, { 0L }),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel to controller
    }

    @Test
    fun debouncesQueryAndPlaysTrackResults() = runTest(dispatcher) {
        val (viewModel, controller) = create()
        viewModel.onIntent(SearchIntent.QueryChanged("Tr"))
        viewModel.onIntent(SearchIntent.QueryChanged("Track 2"))
        advanceUntilIdle()

        val result = viewModel.state.first { it.results.isNotEmpty() }.results.single()
        assertEquals(listOf("2"), result.tracks.items.map { it.id.key })

        viewModel.onIntent(SearchIntent.Play(result.tracks.items, 0))
        advanceUntilIdle()
        assertEquals("2", controller.state.first { it.current != null }.current?.id?.key)
    }

    @Test
    fun scopeLimitsResultsToAlbums() = runTest(dispatcher) {
        val (viewModel, _) = create()
        viewModel.onIntent(SearchIntent.QueryChanged("gruppa"))
        viewModel.onIntent(SearchIntent.ScopeChanged(SearchScope.ALBUMS))
        advanceUntilIdle()

        val result = viewModel.state.first { it.results.isNotEmpty() }.results.single()
        assertEquals(listOf("Gruppa krovi"), result.albums.items.map { it.title })
        assertTrue(result.tracks.items.isEmpty())
    }

    @Test
    fun opensAlbumAndPlaysItsTracks() = runTest(dispatcher) {
        val (viewModel, controller) = create()
        viewModel.onIntent(SearchIntent.QueryChanged("gruppa"))
        advanceUntilIdle()
        val album = viewModel.state.first { it.results.isNotEmpty() }.results.single().albums.items.single()

        viewModel.onIntent(SearchIntent.OpenAlbum(album))
        advanceUntilIdle()
        val collection = viewModel.state.first { it.collection?.loading == false }.collection!!
        assertEquals(listOf("a1", "a2"), collection.tracks.map { it.id.key })

        viewModel.onIntent(SearchIntent.PlayCollection(shuffle = false))
        advanceUntilIdle()
        assertEquals("a1", controller.state.first { it.current != null }.current?.id?.key)

        viewModel.onIntent(SearchIntent.CloseCollection)
        advanceUntilIdle()
        assertNull(viewModel.state.first { it.collection == null }.collection)
    }

    @Test
    fun filtersBySelectedSources() = runTest(dispatcher) {
        val (viewModel, _) = create(radio)
        viewModel.onIntent(SearchIntent.QueryChanged("Track"))
        advanceUntilIdle()
        assertEquals(listOf(TestTracks.SOURCE, SourceId("radio")), viewModel.state.value.sources.map { it.id })
        assertEquals(2, viewModel.state.value.results.size)

        viewModel.onIntent(SearchIntent.ToggleSource(SourceId("radio")))
        advanceUntilIdle()
        assertEquals(setOf(SourceId("radio")), viewModel.state.value.selectedSources)
        assertEquals(listOf(SourceId("radio")), viewModel.state.value.results.map { it.source.id })

        viewModel.onIntent(SearchIntent.ShowAllSources)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.selectedSources.isEmpty())
        assertEquals(2, viewModel.state.value.results.size)
    }
}
