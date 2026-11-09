package space.wavedot.feature.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.create(): Pair<PlaylistPickerViewModel, LibraryTestFixture> {
        val fixture = LibraryTestFixture(this)
        return PlaylistPickerViewModel(fixture.favorites, fixture.playlists, fixture.editor, fixture.toggleFavorite) to fixture
    }

    @Test
    fun showsMembershipForTargets() = runTest(dispatcher) {
        val (viewModel, fixture) = create()
        val full = fixture.editor.create("Full", fixture.tracks.take(2))
        val partial = fixture.editor.create("Partial", fixture.tracks.take(1))
        fixture.toggleFavorite(fixture.tracks[1])

        viewModel.onIntent(PlaylistPickerIntent.Open(fixture.tracks.take(2)))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.visible)
        assertEquals(Membership.PARTIAL, state.favorites)
        assertEquals(
            mapOf(full to Membership.ALL, partial to Membership.PARTIAL),
            state.playlists.associate { it.summary.id to it.membership },
        )
    }

    @Test
    fun togglingPlaylistAddsMissingThenRemoves() = runTest(dispatcher) {
        val (viewModel, fixture) = create()
        val id = fixture.editor.create("Mix", fixture.tracks.take(1))
        val targets = fixture.tracks.take(2)
        viewModel.onIntent(PlaylistPickerIntent.Open(targets))

        viewModel.onIntent(PlaylistPickerIntent.TogglePlaylist(id))
        advanceUntilIdle()
        assertEquals(targets, fixture.playlists.observe(id).first()!!.tracks)

        viewModel.onIntent(PlaylistPickerIntent.TogglePlaylist(id))
        advanceUntilIdle()
        assertEquals(emptyList(), fixture.playlists.observe(id).first()!!.tracks)
    }

    @Test
    fun togglingFavoritesFillsThenClears() = runTest(dispatcher) {
        val (viewModel, fixture) = create()
        fixture.toggleFavorite(fixture.tracks[0])
        viewModel.onIntent(PlaylistPickerIntent.Open(fixture.tracks.take(2)))

        viewModel.onIntent(PlaylistPickerIntent.ToggleFavorites)
        advanceUntilIdle()
        assertEquals(Membership.ALL, viewModel.state.value.favorites)

        viewModel.onIntent(PlaylistPickerIntent.ToggleFavorites)
        advanceUntilIdle()
        assertEquals(Membership.NONE, viewModel.state.value.favorites)
    }

    @Test
    fun createAddsTargetsAndDismissHides() = runTest(dispatcher) {
        val (viewModel, fixture) = create()
        viewModel.onIntent(PlaylistPickerIntent.Open(listOf(fixture.tracks[2])))
        viewModel.onIntent(PlaylistPickerIntent.Create("Fresh"))
        advanceUntilIdle()

        val created = viewModel.state.value.playlists.single()
        assertEquals("Fresh", created.summary.title)
        assertEquals(Membership.ALL, created.membership)

        viewModel.onIntent(PlaylistPickerIntent.Dismiss)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.visible)
    }
}
