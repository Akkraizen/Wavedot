package space.wavedot.feature.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.domain.player.FixedPlaybackPreferences
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.domain.player.ResolveStream
import space.wavedot.core.domain.search.SearchTracks
import space.wavedot.core.testing.FakeAudioEngine
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.InMemoryFavoritesRepository
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun debouncesQueryAndAggregatesResults() = runTest(dispatcher) {
        val registry = TestRegistry(FakeMediaSource(tracks = TestTracks.tracks(3)))
        val favorites = InMemoryFavoritesRepository()
        val controller = PlayerController(FakeAudioEngine(), ResolveStream(registry), FixedPlaybackPreferences(), backgroundScope)
        val viewModel = SearchViewModel(SearchTracks(registry), favorites, controller, ToggleFavorite(favorites, registry, { 0L }))
        backgroundScope.launch { viewModel.state.collect {} }

        viewModel.onIntent(SearchIntent.QueryChanged("Tr"))
        viewModel.onIntent(SearchIntent.QueryChanged("Track 2"))
        advanceUntilIdle()

        val state = viewModel.state.first { it.results.isNotEmpty() }
        assertEquals(listOf("2"), state.results.single().page.items.map { it.id.key })

        viewModel.onIntent(SearchIntent.Play(state.results.single(), 0))
        advanceUntilIdle()
        assertEquals("2", controller.state.first { it.current != null }.current?.id?.key)
    }
}
