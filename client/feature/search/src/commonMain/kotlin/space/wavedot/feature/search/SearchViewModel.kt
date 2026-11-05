package space.wavedot.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.wavedot.core.domain.library.FavoritesRepository
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.domain.search.SearchTracks
import space.wavedot.core.domain.search.SourceSearchResult
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SourceSearchResult> = emptyList(),
    val favoriteIds: Set<MediaId> = emptySet(),
    val playingId: MediaId? = null,
)

sealed interface SearchIntent {
    data class QueryChanged(val query: String) : SearchIntent
    data class Play(val source: SourceSearchResult, val index: Int) : SearchIntent
    data class ToggleFavorite(val track: Track) : SearchIntent
    data class PlayNext(val track: Track) : SearchIntent
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val search: SearchTracks,
    favorites: FavoritesRepository,
    private val player: PlayerController,
    private val toggleFavorite: ToggleFavorite,
    debounceMillis: Long = 350,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(false)
    private val results = MutableStateFlow<List<SourceSearchResult>>(emptyList())

    val state: StateFlow<SearchUiState> = combine(
        query, loading, results, favorites.observeIds(), player.state,
    ) { q, isLoading, found, ids, playerState ->
        SearchUiState(q, isLoading, found, ids, playerState.current?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        query
            .debounce(debounceMillis)
            .distinctUntilChanged()
            .onEach { loading.value = it.isNotBlank() }
            .mapLatest { if (it.isBlank()) emptyList() else search(it) }
            .onEach {
                results.value = it
                loading.value = false
            }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> query.value = intent.query
            is SearchIntent.Play -> player.play(intent.source.page.items, intent.index)
            is SearchIntent.ToggleFavorite -> viewModelScope.launch { toggleFavorite(intent.track) }
            is SearchIntent.PlayNext -> player.playNext(intent.track)
        }
    }
}
