package space.wavedot.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.wavedot.core.domain.library.FavoritesRepository
import space.wavedot.core.domain.library.LocalLibrary
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track

enum class LibraryTab { FAVORITES, LOCAL }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.FAVORITES,
    val favorites: List<Track> = emptyList(),
    val local: List<Track> = emptyList(),
    val favoriteIds: Set<MediaId> = emptySet(),
    val playingId: MediaId? = null,
    val scanning: Boolean = false,
) {
    val visible: List<Track> get() = if (tab == LibraryTab.FAVORITES) favorites else local
}

sealed interface LibraryIntent {
    data class SelectTab(val tab: LibraryTab) : LibraryIntent
    data class Play(val index: Int) : LibraryIntent
    data class ToggleFavorite(val track: Track) : LibraryIntent
    data object Rescan : LibraryIntent
    data object ShuffleAll : LibraryIntent
}

class LibraryViewModel(
    favorites: FavoritesRepository,
    private val localLibrary: LocalLibrary,
    private val player: PlayerController,
    private val toggleFavorite: ToggleFavorite,
) : ViewModel() {
    private val tab = MutableStateFlow(LibraryTab.FAVORITES)
    private val scanning = MutableStateFlow(false)

    val state: StateFlow<LibraryUiState> = combine(
        tab,
        favorites.observeAll(),
        localLibrary.tracks,
        favorites.observeIds(),
        combine(player.state, scanning) { p, s -> p.current?.id to s },
    ) { selected, favoriteList, local, ids, (playing, isScanning) ->
        LibraryUiState(selected, favoriteList.map { it.track }, local, ids, playing, isScanning)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.SelectTab -> tab.value = intent.tab
            is LibraryIntent.Play -> player.play(state.value.visible, intent.index)
            is LibraryIntent.ToggleFavorite -> viewModelScope.launch { toggleFavorite(intent.track) }
            LibraryIntent.Rescan -> viewModelScope.launch {
                scanning.value = true
                try {
                    localLibrary.rescan()
                } finally {
                    scanning.value = false
                }
            }
            LibraryIntent.ShuffleAll -> state.value.visible.takeIf { it.isNotEmpty() }?.let {
                player.setShuffle(true)
                player.play(it, 0)
            }
        }
    }
}
