package space.wavedot.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import space.wavedot.core.domain.library.PlaylistDetails
import space.wavedot.core.domain.library.PlaylistEntry
import space.wavedot.core.domain.library.PlaylistRepository
import space.wavedot.core.domain.library.PlaylistSummary
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import kotlin.time.Duration

class InMemoryPlaylistRepository : PlaylistRepository {
    private data class Stored(
        val title: String,
        val createdAt: Long,
        val updatedAt: Long,
        val entries: List<PlaylistEntry>,
    )

    private val items = MutableStateFlow<Map<String, Stored>>(emptyMap())

    override fun observeAll(): Flow<List<PlaylistSummary>> = items.map { map ->
        map.map { (id, stored) -> stored.summary(id) }.sortedByDescending { it.updatedAt }
    }

    override fun observe(id: String): Flow<PlaylistDetails?> = items.map { map ->
        map[id]?.let { PlaylistDetails(it.summary(id), it.entries) }
    }

    override fun observeContaining(trackIds: Collection<MediaId>): Flow<Map<String, Int>> = items.map { map ->
        val wanted = trackIds.toSet()
        map.mapValues { (_, stored) -> stored.entries.count { it.track.id in wanted } }.filterValues { it > 0 }
    }

    override suspend fun exists(id: String): Boolean = id in items.value

    override suspend fun create(id: String, title: String, at: Long) {
        items.value = items.value + (id to Stored(title, at, at, emptyList()))
    }

    override suspend fun rename(id: String, title: String, at: Long) {
        update(id) { it.copy(title = title, updatedAt = at) }
    }

    override suspend fun delete(id: String) {
        items.value = items.value - id
    }

    override suspend fun contained(id: String, trackIds: Collection<MediaId>): Set<MediaId> {
        val present = items.value[id]?.entries.orEmpty().mapTo(mutableSetOf()) { it.track.id }
        return trackIds.filterTo(mutableSetOf()) { it in present }
    }

    override suspend fun add(id: String, tracks: List<Track>, at: Long): Int {
        var added = 0
        update(id) { stored ->
            val present = stored.entries.mapTo(mutableSetOf()) { it.track.id }
            val fresh = tracks.filter { present.add(it.id) }.map { PlaylistEntry(it, at) }
            added = fresh.size
            stored.copy(entries = stored.entries + fresh, updatedAt = at)
        }
        return added
    }

    override suspend fun remove(id: String, trackIds: Collection<MediaId>, at: Long) {
        val removed = trackIds.toSet()
        update(id) { stored -> stored.copy(entries = stored.entries.filter { it.track.id !in removed }, updatedAt = at) }
    }

    private fun update(id: String, transform: (Stored) -> Stored) {
        val current = items.value[id] ?: return
        items.value = items.value + (id to transform(current))
    }

    private fun Stored.summary(id: String) = PlaylistSummary(
        id = id,
        title = title,
        trackCount = entries.size,
        duration = entries.fold(Duration.ZERO) { total, entry -> total + (entry.track.duration ?: Duration.ZERO) },
        covers = entries.mapNotNull { it.track.artwork?.url }.distinct().take(4),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
