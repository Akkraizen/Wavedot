package space.wavedot.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import space.wavedot.core.common.toWavedotError
import space.wavedot.core.contract.SourceDescriptor
import space.wavedot.core.domain.library.FavoritesRepository
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.domain.search.LoadCollection
import space.wavedot.core.domain.search.SearchCatalog
import space.wavedot.core.domain.search.SearchScope
import space.wavedot.core.domain.search.SourceSearchResult
import space.wavedot.core.model.Album
import space.wavedot.core.model.Artist
import space.wavedot.core.model.EntityKind
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track

data class CollectionUi(
    val id: MediaId,
    val kind: EntityKind,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val sources: List<SourceDescriptor> = emptyList(),
    val selectedSources: Set<SourceId> = emptySet(),
    val loading: Boolean = false,
    val results: List<SourceSearchResult> = emptyList(),
    val favoriteIds: Set<MediaId> = emptySet(),
    val playingId: MediaId? = null,
    val collection: CollectionUi? = null,
)

sealed interface SearchIntent {
    data class QueryChanged(val query: String) : SearchIntent
    data class ScopeChanged(val scope: SearchScope) : SearchIntent
    data class ToggleSource(val id: SourceId) : SearchIntent
    data object ShowAllSources : SearchIntent
    data class Play(val tracks: List<Track>, val index: Int) : SearchIntent
    data class ToggleFavorite(val track: Track) : SearchIntent
    data class PlayNext(val track: Track) : SearchIntent
    data class OpenAlbum(val album: Album) : SearchIntent
    data class OpenArtist(val artist: Artist) : SearchIntent
    data object CloseCollection : SearchIntent
    data class PlayCollection(val shuffle: Boolean) : SearchIntent
}

private data class SearchRequest(
    val query: String,
    val scope: SearchScope,
    val sources: Set<SourceId>,
    val available: Set<SourceId>,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val search: SearchCatalog,
    private val loadCollection: LoadCollection,
    favorites: FavoritesRepository,
    private val player: PlayerController,
    private val toggleFavorite: ToggleFavorite,
    debounceMillis: Long = 350,
) : ViewModel() {
    private val local = MutableStateFlow(SearchUiState())
    private var collectionJob: Job? = null

    val state: StateFlow<SearchUiState> = combine(
        local,
        search.sources,
        favorites.observeIds(),
        player.state,
    ) { current, sources, ids, playerState ->
        current.copy(
            sources = sources,
            selectedSources = current.selectedSources.filterTo(mutableSetOf()) { id -> sources.any { it.id == id } },
            favoriteIds = ids,
            playingId = playerState.current?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        combine(local, search.sources) { current, sources ->
            val available = sources.mapTo(mutableSetOf()) { it.id }
            SearchRequest(current.query.trim(), current.scope, current.selectedSources.intersect(available), available)
        }
            .distinctUntilChanged()
            .debounce(debounceMillis)
            .onEach { request -> local.update { it.copy(loading = request.query.isNotEmpty()) } }
            .mapLatest { request ->
                if (request.query.isEmpty()) emptyList() else search(request.query, request.scope, sources = request.sources)
            }
            .onEach { results -> local.update { it.copy(results = results, loading = false) } }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> local.update { it.copy(query = intent.query) }
            is SearchIntent.ScopeChanged -> local.update { it.copy(scope = intent.scope) }
            is SearchIntent.ToggleSource -> local.update { current ->
                val selected = current.selectedSources
                current.copy(selectedSources = if (intent.id in selected) selected - intent.id else selected + intent.id)
            }
            SearchIntent.ShowAllSources -> local.update { it.copy(selectedSources = emptySet()) }
            is SearchIntent.Play -> player.play(intent.tracks, intent.index)
            is SearchIntent.ToggleFavorite -> viewModelScope.launch { toggleFavorite(intent.track) }
            is SearchIntent.PlayNext -> player.playNext(intent.track)
            is SearchIntent.OpenAlbum -> open(
                CollectionUi(
                    id = intent.album.id,
                    kind = EntityKind.ALBUM,
                    title = intent.album.title,
                    subtitle = listOfNotNull(intent.album.artists.firstOrNull()?.name, intent.album.year?.toString())
                        .joinToString(" · ")
                        .ifEmpty { null },
                    artworkUrl = intent.album.artwork?.url,
                ),
            )
            is SearchIntent.OpenArtist -> open(
                CollectionUi(
                    id = intent.artist.id,
                    kind = EntityKind.ARTIST,
                    title = intent.artist.name,
                    subtitle = null,
                    artworkUrl = intent.artist.artwork?.url,
                ),
            )
            SearchIntent.CloseCollection -> {
                collectionJob?.cancel()
                local.update { it.copy(collection = null) }
            }
            is SearchIntent.PlayCollection -> {
                val tracks = local.value.collection?.tracks.orEmpty()
                if (tracks.isNotEmpty()) {
                    player.setShuffle(intent.shuffle)
                    player.play(tracks, 0)
                }
            }
        }
    }

    private fun open(collection: CollectionUi) {
        collectionJob?.cancel()
        local.update { it.copy(collection = collection) }
        collectionJob = viewModelScope.launch {
            val loaded = try {
                collection.copy(tracks = loadCollection(collection.id, collection.kind), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                collection.copy(loading = false, error = t.toWavedotError().message)
            }
            local.update { current -> if (current.collection?.id == collection.id) current.copy(collection = loaded) else current }
        }
    }
}
