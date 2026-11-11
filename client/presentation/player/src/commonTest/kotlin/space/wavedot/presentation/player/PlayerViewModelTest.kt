package space.wavedot.presentation.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.domain.player.FixedPlaybackPreferences
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.domain.player.ResolveStream
import space.wavedot.core.testing.FakeAudioEngine
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.InMemoryFavoritesRepository
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun exposesCurrentTrackAndFavoriteState() = runTest(dispatcher) {
        val tracks = TestTracks.tracks(2)
        val registry = TestRegistry(FakeMediaSource(tracks = tracks))
        val favorites = InMemoryFavoritesRepository()
        val controller = PlayerController(FakeAudioEngine(), ResolveStream(registry), FixedPlaybackPreferences(), backgroundScope)
        val viewModel = PlayerViewModel(controller, favorites, ToggleFavorite(favorites, registry, { 0L }))
        backgroundScope.launch { viewModel.state.collect {} }

        controller.play(tracks)
        viewModel.onIntent(PlayerIntent.ToggleFavorite)

        val state = viewModel.state.first { it.favorite }
        assertEquals(tracks[0], state.track)
        assertTrue(state.isPlaying)
        assertEquals(2, state.queue.size)
    }
}
