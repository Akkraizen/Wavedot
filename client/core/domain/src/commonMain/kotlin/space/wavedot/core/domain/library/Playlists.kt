package space.wavedot.core.domain.library

import kotlinx.coroutines.flow.Flow
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import kotlin.time.Duration

data class PlaylistSummary(
    val id: String,
    val title: String,
    val trackCount: Int,
    val duration: Duration,
    val covers: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

data class PlaylistEntry(
    val track: Track,
    val addedAt: Long,
)

data class PlaylistDetails(
    val summary: PlaylistSummary,
    val entries: List<PlaylistEntry>,
) {
    val tracks: List<Track> get() = entries.map { it.track }
}

interface PlaylistRepository {
    fun observeAll(): Flow<List<PlaylistSummary>>

    fun observe(id: String): Flow<PlaylistDetails?>

    fun observeContaining(trackIds: Collection<MediaId>): Flow<Map<String, Int>>

    suspend fun exists(id: String): Boolean

    suspend fun create(id: String, title: String, at: Long)

    suspend fun rename(id: String, title: String, at: Long)

    suspend fun delete(id: String)

    suspend fun contained(id: String, trackIds: Collection<MediaId>): Set<MediaId>

    suspend fun add(id: String, tracks: List<Track>, at: Long): Int

    suspend fun remove(id: String, trackIds: Collection<MediaId>, at: Long)
}
