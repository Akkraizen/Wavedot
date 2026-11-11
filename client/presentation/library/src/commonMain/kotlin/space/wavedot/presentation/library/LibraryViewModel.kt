package space.wavedot.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.wavedot.core.domain.library.FavoritesRepository
import space.wavedot.core.domain.library.LocalLibrary
import space.wavedot.core.domain.library.PlaylistEditor
import space.wavedot.core.domain.library.PlaylistRepository
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import kotlin.time.Duration

sealed interface LibraryCollection {
    data object Favorites : LibraryCollection
    data object Local : LibraryCollection
    data class Playlist(val id: String) : LibraryCollection
}

data class LibraryShelf(
    val collection: LibraryCollection,
    val title: String,
    val trackCount: Int,
    val covers: List<String>,
)

data class CollectionDetails(
    val collection: LibraryCollection,
    val title: String,
    val tracks: List<Track>,
    val covers: List<String>,
) {
    val duration: Duration get() = tracks.fold(Duration.ZERO) { total, track -> total + (track.duration ?: Duration.ZERO) }
    val editable: Boolean get() = collection is LibraryCollection.Playlist
}

data class LibraryUiState(
    val shelves: List<LibraryShelf> = emptyList(),
    val opened: CollectionDetails? = null,
    val favoriteIds: Set<MediaId> = emptySet(),
    val playingId: MediaId? = null,
    val scanning: Boolean = false,
) {
    val playlistCount: Int get() = shelves.count { it.collection is LibraryCollection.Playlist }
}

sealed interface LibraryIntent {
    data class Open(val collection: LibraryCollection) : LibraryIntent
    data object Close : LibraryIntent
    data class Play(val index: Int) : LibraryIntent
    data class PlayAll(val shuffle: Boolean) : LibraryIntent
    data class PlayNext(val track: Track) : LibraryIntent
    data class ToggleFavorite(val track: Track) : LibraryIntent
    data class RemoveFromCollection(val track: Track) : LibraryIntent
    data class CreatePlaylist(val title: String) : LibraryIntent
    data class RenamePlaylist(val title: String) : LibraryIntent
    data object DeletePlaylist : LibraryIntent
    data object Rescan : LibraryIntent
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val favorites: FavoritesRepository,
    private val localLibrary: LocalLibrary,
    private val playlists: PlaylistRepository,
    private val editor: PlaylistEditor,
    private val player: PlayerController,
    private val toggleFavorite: ToggleFavorite,
) : ViewModel() {
    private val selected = MutableStateFlow<LibraryCollection?>(null)
    private val scanning = MutableStateFlow(false)
    private val favoriteTracks = favorites.observeAll().map { list -> list.map { it.track } }

    private val shelves: Flow<List<LibraryShelf>> = combine(
        favoriteTracks,
        localLibrary.tracks,
        playlists.observeAll(),
    ) { favoriteList, local, userPlaylists ->
        listOf(
            LibraryShelf(LibraryCollection.Favorites, FAVORITES_TITLE, favoriteList.size, covers(favoriteList)),
            LibraryShelf(LibraryCollection.Local, LOCAL_TITLE, local.size, covers(local)),
        ) + userPlaylists.map { LibraryShelf(LibraryCollection.Playlist(it.id), it.title, it.trackCount, it.covers) }
    }

    private val opened: Flow<CollectionDetails?> = selected.flatMapLatest { collection ->
        when (collection) {
            null -> flowOf(null)
            LibraryCollection.Favorites -> favoriteTracks.map { CollectionDetails(collection, FAVORITES_TITLE, it, covers(it)) }
            LibraryCollection.Local -> localLibrary.tracks.map { CollectionDetails(collection, LOCAL_TITLE, it, covers(it)) }
            is LibraryCollection.Playlist -> playlists.observe(collection.id).map { details ->
                details?.let { CollectionDetails(collection, it.summary.title, it.tracks, it.summary.covers) }
            }
        }
    }

    val state: StateFlow<LibraryUiState> = combine(
        shelves,
        opened,
        favorites.observeIds(),
        player.state,
        scanning,
    ) { shelfList, details, ids, playerState, isScanning ->
        LibraryUiState(shelfList, details, ids, playerState.current?.id, isScanning)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.Open -> selected.value = intent.collection
            LibraryIntent.Close -> selected.value = null
            is LibraryIntent.Play -> openedTracks().takeIf { intent.index in it.indices }?.let { player.play(it, intent.index) }
            is LibraryIntent.PlayAll -> openedTracks().takeIf { it.isNotEmpty() }?.let {
                player.setShuffle(intent.shuffle)
                player.play(it, 0)
            }
            is LibraryIntent.PlayNext -> player.playNext(intent.track)
            is LibraryIntent.ToggleFavorite -> viewModelScope.launch { toggleFavorite(intent.track) }
            is LibraryIntent.RemoveFromCollection -> removeFromCollection(intent.track)
            is LibraryIntent.CreatePlaylist -> viewModelScope.launch {
                selected.value = LibraryCollection.Playlist(editor.create(intent.title))
            }
            is LibraryIntent.RenamePlaylist -> selectedPlaylist()?.let { id -> viewModelScope.launch { editor.rename(id, intent.title) } }
            LibraryIntent.DeletePlaylist -> selectedPlaylist()?.let { id ->
                selected.value = null
                viewModelScope.launch { editor.delete(id) }
            }
            LibraryIntent.Rescan -> viewModelScope.launch {
                scanning.value = true
                try {
                    localLibrary.rescan()
                } finally {
                    scanning.value = false
                }
            }
        }
    }

    private fun removeFromCollection(track: Track) {
        when (val collection = selected.value) {
            LibraryCollection.Favorites -> viewModelScope.launch { toggleFavorite(track) }
            is LibraryCollection.Playlist -> viewModelScope.launch { editor.remove(collection.id, listOf(track)) }
            LibraryCollection.Local, null -> Unit
        }
    }

    private fun openedTracks(): List<Track> = state.value.opened?.tracks.orEmpty()

    private fun selectedPlaylist(): String? = (selected.value as? LibraryCollection.Playlist)?.id

    private fun covers(tracks: List<Track>): List<String> =
        tracks.asSequence().mapNotNull { it.artwork?.url }.distinct().take(MAX_COVERS).toList()

    companion object {
        const val FAVORITES_TITLE: String = "Favorites"
        const val LOCAL_TITLE: String = "On this device"
        private const val MAX_COVERS = 4
    }
}
