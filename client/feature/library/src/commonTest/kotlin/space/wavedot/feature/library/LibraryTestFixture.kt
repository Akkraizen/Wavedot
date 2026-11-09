package space.wavedot.feature.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import space.wavedot.core.domain.library.LocalLibrary
import space.wavedot.core.domain.library.PlaylistEditor
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.domain.player.FixedPlaybackPreferences
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.domain.player.ResolveStream
import space.wavedot.core.model.Track
import space.wavedot.core.testing.FakeAudioEngine
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.InMemoryFavoritesRepository
import space.wavedot.core.testing.InMemoryPlaylistRepository
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks

class FakeLocalLibrary(initial: List<Track> = emptyList()) : LocalLibrary {
    override val tracks = MutableStateFlow(initial)
    var rescans = 0

    override suspend fun rescan(): Int {
        rescans++
        return tracks.value.size
    }
}

class LibraryTestFixture(scope: TestScope) {
    val tracks: List<Track> = TestTracks.tracks(4)
    val registry = TestRegistry(FakeMediaSource(tracks = tracks))
    val favorites = InMemoryFavoritesRepository()
    val playlists = InMemoryPlaylistRepository()
    val local = FakeLocalLibrary(tracks.take(2))
    private var nextId = 0
    val editor = PlaylistEditor(playlists, { 10L }, { "p${++nextId}" })
    val toggleFavorite = ToggleFavorite(favorites, registry, { 5L })
    val player = PlayerController(FakeAudioEngine(), ResolveStream(registry), FixedPlaybackPreferences(), scope.backgroundScope)
}
