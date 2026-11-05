package space.wavedot.core.domain.library

import kotlinx.coroutines.flow.Flow
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track

data class Favorite(
    val track: Track,
    val addedAt: Long,
)

data class FavoriteChange(
    val id: MediaId,
    val favorite: Favorite?,
    val updatedAt: Long,
) {
    val deleted: Boolean get() = favorite == null
}

enum class ChangeOrigin { LOCAL, REMOTE }

interface FavoritesRepository {
    fun observeAll(): Flow<List<Favorite>>

    fun observeIds(): Flow<Set<MediaId>>

    suspend fun isFavorite(id: MediaId): Boolean

    suspend fun add(track: Track, at: Long, origin: ChangeOrigin = ChangeOrigin.LOCAL)

    suspend fun remove(id: MediaId, at: Long, origin: ChangeOrigin = ChangeOrigin.LOCAL)

    suspend fun pendingChanges(): List<FavoriteChange>

    suspend fun markSynced(changes: List<FavoriteChange>)

    suspend fun applyRemote(changes: List<FavoriteChange>)
}
