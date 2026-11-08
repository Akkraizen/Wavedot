package space.wavedot.core.domain.search

import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.contract.PagedQuery
import space.wavedot.core.model.Cursor
import space.wavedot.core.model.EntityKind
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track

class LoadCollection(
    private val registry: MediaSourceRegistry,
    private val maxTracks: Int = DEFAULT_MAX_TRACKS,
) {
    suspend operator fun invoke(id: MediaId, kind: EntityKind): List<Track> {
        val catalog = registry.require(id.source).catalog
            ?: throw WavedotException(WavedotError.Unsupported("catalog of ${id.source.value}"))
        val query = when (kind) {
            EntityKind.ALBUM -> catalog.albumTracks
            EntityKind.ARTIST -> catalog.artistTracks
            EntityKind.PLAYLIST -> catalog.playlistTracks
            EntityKind.TRACK -> null
        } ?: throw WavedotException(WavedotError.Unsupported("${kind.name.lowercase()} tracks in ${id.source.value}"))
        return collect(query, id)
    }

    private suspend fun collect(query: PagedQuery<MediaId, Track>, id: MediaId): List<Track> {
        val result = mutableListOf<Track>()
        var cursor: Cursor? = null
        do {
            val page = query.page(id, cursor)
            result += page.items
            cursor = page.next
        } while (cursor != null && result.size < maxTracks)
        return result.take(maxTracks)
    }

    companion object {
        const val DEFAULT_MAX_TRACKS: Int = 500
    }
}
