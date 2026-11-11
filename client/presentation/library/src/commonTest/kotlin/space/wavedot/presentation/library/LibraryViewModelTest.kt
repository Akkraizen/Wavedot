package space.wavedot.presentation.library

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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.create(): Pair<LibraryViewModel, LibraryTestFixture> {
        val fixture = LibraryTestFixture(this)
        val viewModel = LibraryViewModel(
            fixture.favorites,
            fixture.local,
            fixture.playlists,
            fixture.editor,
            fixture.player,
            fixture.toggleFavorite,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel to fixture
    }

    @Test
    fun libraryListsFavoritesLocalAndPlaylists() = runTest(dispatcher) {
        val (viewModel, fixture) = create()
        fixture.editor.create("Road trip", fixture.tracks.take(3))
        fixture.toggleFavorite(fixture.tracks[3])
        advanceUntilIdle()

        val shelves = viewModel.state.value.shelves
        assertEquals(
            listOf(LibraryCollection.Favorites, LibraryCollection.Local, LibraryCollection.Playlist("p1")),
            shelves.map { it.collection },
        )
        assertEquals(listOf(1, 2, 3), shelves.map { it.trackCount })
        assertEquals("Road trip", shelves.last().title)
        assertEquals(1, viewModel.state.value.playlistCount)
    }

    @Test
    fun creatingPlaylistOpensIt() = runTest(dispatcher) {
        val (viewModel, _) = create()
        viewModel.onIntent(LibraryIntent.CreatePlaylist("Evening"))
        advanceUntilIdle()

        val opened = viewModel.state.value.opened!!
        assertEquals(LibraryCollection.Playlist("p1"), opened.collection)
        assertEquals("Evening", opened.title)
        assertEquals(true, opened.editable)
    }

    @Test
    fun removingFromOpenedPlaylistAndFavorites() = runTest(dispatcher) {
        val (viewModel, fixture) = create()
        val id = fixture.editor.create("Mix", fixture.tracks.take(2))
        fixture.toggleFavorite(fixture.tracks[0])

        viewModel.onIntent(LibraryIntent.Open(LibraryCollection.Playlist(id)))
        advanceUntilIdle()
        viewModel.onIntent(LibraryIntent.RemoveFromCollection(fixture.tracks[0]))
        advanceUntilIdle()
        assertEquals(listOf(fixture.tracks[1]), viewModel.state.value.opened!!.tracks)

        viewModel.onIntent(LibraryIntent.Open(LibraryCollection.Favorites))
        advanceUntilIdle()
        viewModel.onIntent(LibraryIntent.RemoveFromCollection(fixture.tracks[0]))
        advanceUntilIdle()
        assertEquals(emptyList(), viewModel.state.value.opened!!.tracks)
    }

    @Test
    fun renameAndDelete() = runTest(dispatcher) {
        val (viewModel, fixture) = create()
        val id = fixture.editor.create("Mix")
        viewModel.onIntent(LibraryIntent.Open(LibraryCollection.Playlist(id)))
        viewModel.onIntent(LibraryIntent.RenamePlaylist("Night drive"))
        advanceUntilIdle()
        assertEquals("Night drive", viewModel.state.value.opened!!.title)

        viewModel.onIntent(LibraryIntent.DeletePlaylist)
        advanceUntilIdle()
        assertNull(viewModel.state.value.opened)
        assertEquals(2, viewModel.state.value.shelves.size)
        assertNull(fixture.playlists.observe(id).first())
    }

    @Test
    fun playsOpenedCollection() = runTest(dispatcher) {
        val (viewModel, fixture) = create()
        viewModel.onIntent(LibraryIntent.Open(LibraryCollection.Local))
        advanceUntilIdle()
        viewModel.onIntent(LibraryIntent.Play(1))
        advanceUntilIdle()
        assertEquals(fixture.tracks[1], fixture.player.state.first { it.current != null }.current)
    }
}
