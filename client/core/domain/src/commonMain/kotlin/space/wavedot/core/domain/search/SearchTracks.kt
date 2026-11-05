package space.wavedot.core.domain.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.toWavedotError
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.contract.SourceDescriptor
import space.wavedot.core.model.Cursor
import space.wavedot.core.model.Page
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track

data class SourceSearchResult(
    val source: SourceDescriptor,
    val page: Page<Track> = Page.empty(),
    val error: WavedotError? = null,
)

class SearchTracks(private val registry: MediaSourceRegistry) {
    suspend operator fun invoke(
        text: String,
        limit: Int = DEFAULT_LIMIT,
        only: SourceId? = null,
        cursor: Cursor? = null,
    ): List<SourceSearchResult> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
        val targets = registry.sources.value
            .filter { it.search != null }
            .filter { only == null || it.descriptor.id == only }
        return coroutineScope {
            targets.map { source ->
                async {
                    try {
                        val page = source.search!!.tracks.page(SearchQuery(query), cursor, limit)
                        SourceSearchResult(source.descriptor, page)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        SourceSearchResult(source.descriptor, error = t.toWavedotError())
                    }
                }
            }.awaitAll()
        }
    }

    companion object {
        const val DEFAULT_LIMIT: Int = 20
    }
}
