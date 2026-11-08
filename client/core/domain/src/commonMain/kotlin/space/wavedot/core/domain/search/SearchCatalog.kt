package space.wavedot.core.domain.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.toWavedotError
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.contract.PagedQuery
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.contract.SourceDescriptor
import space.wavedot.core.model.Album
import space.wavedot.core.model.Artist
import space.wavedot.core.model.EntityKind
import space.wavedot.core.model.Page
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track

enum class SearchScope(val kinds: Set<EntityKind>) {
    ALL(setOf(EntityKind.TRACK, EntityKind.ALBUM, EntityKind.ARTIST)),
    TRACKS(setOf(EntityKind.TRACK)),
    ALBUMS(setOf(EntityKind.ALBUM)),
    ARTISTS(setOf(EntityKind.ARTIST)),
}

data class SourceSearchResult(
    val source: SourceDescriptor,
    val tracks: Page<Track> = Page.empty(),
    val albums: Page<Album> = Page.empty(),
    val artists: Page<Artist> = Page.empty(),
    val error: WavedotError? = null,
) {
    val isEmpty: Boolean get() = tracks.items.isEmpty() && albums.items.isEmpty() && artists.items.isEmpty()
}

class SearchCatalog(private val registry: MediaSourceRegistry) {
    suspend operator fun invoke(
        text: String,
        scope: SearchScope = SearchScope.ALL,
        limit: Int = DEFAULT_LIMIT,
        only: SourceId? = null,
    ): List<SourceSearchResult> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
        val targets = registry.sources.value
            .filter { it.search != null }
            .filter { only == null || it.descriptor.id == only }
        return coroutineScope {
            targets
                .map { source -> async { searchSource(source, SearchQuery(query, scope.kinds), limit) } }
                .awaitAll()
        }
    }

    private suspend fun searchSource(source: MediaSource, query: SearchQuery, limit: Int): SourceSearchResult {
        val search = source.search!!
        val errors = mutableListOf<WavedotError>()
        suspend fun <T> attempt(kind: EntityKind, paged: PagedQuery<SearchQuery, T>?): Page<T> {
            if (kind !in query.kinds || paged == null) return Page.empty()
            return try {
                paged.page(query, limit = limit)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                errors += t.toWavedotError()
                Page.empty()
            }
        }
        return coroutineScope {
            val tracks = async { attempt(EntityKind.TRACK, search.tracks) }
            val albums = async { attempt(EntityKind.ALBUM, search.albums) }
            val artists = async { attempt(EntityKind.ARTIST, search.artists) }
            SourceSearchResult(source.descriptor, tracks.await(), albums.await(), artists.await(), errors.firstOrNull())
        }
    }

    companion object {
        const val DEFAULT_LIMIT: Int = 20
    }
}
