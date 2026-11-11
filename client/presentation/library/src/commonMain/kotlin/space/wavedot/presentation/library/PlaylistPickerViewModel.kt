package space.wavedot.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.wavedot.core.domain.library.FavoritesRepository
import space.wavedot.core.domain.library.PlaylistEditor
import space.wavedot.core.domain.library.PlaylistRepository
import space.wavedot.core.domain.library.PlaylistSummary
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.model.Track

enum class Membership { NONE, PARTIAL, ALL }

data class PickerPlaylist(
    val summary: PlaylistSummary,
    val membership: Membership,
)

data class PlaylistPickerUiState(
    val tracks: List<Track> = emptyList(),
    val favorites: Membership = Membership.NONE,
    val playlists: List<PickerPlaylist> = emptyList(),
) {
    val visible: Boolean get() = tracks.isNotEmpty()
}

sealed interface PlaylistPickerIntent {
    data class Open(val tracks: List<Track>) : PlaylistPickerIntent
    data object Dismiss : PlaylistPickerIntent
    data object ToggleFavorites : PlaylistPickerIntent
    data class TogglePlaylist(val id: String) : PlaylistPickerIntent
    data class Create(val title: String) : PlaylistPickerIntent
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPickerViewModel(
    private val favorites: FavoritesRepository,
    private val playlists: PlaylistRepository,
    private val editor: PlaylistEditor,
    private val toggleFavorite: ToggleFavorite,
) : ViewModel() {
    private val targets = MutableStateFlow<List<Track>>(emptyList())

    val state: StateFlow<PlaylistPickerUiState> = targets.flatMapLatest { tracks ->
        if (tracks.isEmpty()) return@flatMapLatest flowOf(PlaylistPickerUiState())
        val ids = tracks.map { it.id }
        combine(
            playlists.observeAll(),
            playlists.observeContaining(ids),
            favorites.observeIds(),
        ) { summaries, containing, favoriteIds ->
            PlaylistPickerUiState(
                tracks = tracks,
                favorites = membership(ids.count { it in favoriteIds }, ids.size),
                playlists = summaries
                    .sortedByDescending { it.createdAt }
                    .map { PickerPlaylist(it, membership(containing[it.id] ?: 0, ids.size)) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistPickerUiState())

    fun onIntent(intent: PlaylistPickerIntent) {
        when (intent) {
            is PlaylistPickerIntent.Open -> targets.value = intent.tracks.distinctBy { it.id }
            PlaylistPickerIntent.Dismiss -> targets.value = emptyList()
            PlaylistPickerIntent.ToggleFavorites -> toggleFavorites(targets.value)
            is PlaylistPickerIntent.TogglePlaylist -> targets.value.takeIf { it.isNotEmpty() }?.let { tracks ->
                viewModelScope.launch { editor.toggle(intent.id, tracks) }
            }
            is PlaylistPickerIntent.Create -> targets.value.takeIf { it.isNotEmpty() }?.let { tracks ->
                viewModelScope.launch { editor.create(intent.title, tracks) }
            }
        }
    }

    private fun toggleFavorites(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val favoriteIds = favorites.observeIds().first()
            val changing = if (tracks.all { it.id in favoriteIds }) tracks else tracks.filter { it.id !in favoriteIds }
            changing.forEach { toggleFavorite(it) }
        }
    }

    private fun membership(contained: Int, total: Int): Membership = when {
        contained <= 0 -> Membership.NONE
        contained >= total -> Membership.ALL
        else -> Membership.PARTIAL
    }
}
