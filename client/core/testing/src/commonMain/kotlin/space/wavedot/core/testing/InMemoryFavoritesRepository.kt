package space.wavedot.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import space.wavedot.core.domain.library.ChangeOrigin
import space.wavedot.core.domain.library.Favorite
import space.wavedot.core.domain.library.FavoriteChange
import space.wavedot.core.domain.library.FavoritesRepository
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track

class InMemoryFavoritesRepository : FavoritesRepository {
    private val items = MutableStateFlow<Map<MediaId, Favorite>>(emptyMap())
    private val pending = linkedMapOf<MediaId, FavoriteChange>()

    override fun observeAll(): Flow<List<Favorite>> = items.map { map -> map.values.sortedByDescending { it.addedAt } }

    override fun observeIds(): Flow<Set<MediaId>> = items.map { it.keys }

    override suspend fun isFavorite(id: MediaId): Boolean = id in items.value

    override suspend fun add(track: Track, at: Long, origin: ChangeOrigin) {
        val favorite = Favorite(track, at)
        items.value = items.value + (track.id to favorite)
        if (origin == ChangeOrigin.LOCAL) pending[track.id] = FavoriteChange(track.id, favorite, at)
    }

    override suspend fun remove(id: MediaId, at: Long, origin: ChangeOrigin) {
        items.value = items.value - id
        if (origin == ChangeOrigin.LOCAL) pending[id] = FavoriteChange(id, null, at)
    }

    override suspend fun pendingChanges(): List<FavoriteChange> = pending.values.toList()

    override suspend fun markSynced(changes: List<FavoriteChange>) {
        changes.forEach { change -> if (pending[change.id] == change) pending.remove(change.id) }
    }

    override suspend fun applyRemote(changes: List<FavoriteChange>) {
        changes.forEach { change ->
            val favorite = change.favorite
            if (favorite == null) remove(change.id, change.updatedAt, ChangeOrigin.REMOTE)
            else add(favorite.track, favorite.addedAt, ChangeOrigin.REMOTE)
        }
    }
}
