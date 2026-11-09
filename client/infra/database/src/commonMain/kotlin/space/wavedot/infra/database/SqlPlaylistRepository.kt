package space.wavedot.infra.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import space.wavedot.core.domain.library.PlaylistDetails
import space.wavedot.core.domain.library.PlaylistEntry
import space.wavedot.core.domain.library.PlaylistRepository
import space.wavedot.core.domain.library.PlaylistSummary
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import kotlin.time.Duration.Companion.milliseconds

class SqlPlaylistRepository(
    private val database: WavedotDatabase,
    private val dispatcher: CoroutineDispatcher,
) : PlaylistRepository {
    private val queries get() = database.playlistQueries

    override fun observeAll(): Flow<List<PlaylistSummary>> = combine(
        queries.selectSummaries().asFlow().mapToList(dispatcher),
        queries.selectCovers().asFlow().mapToList(dispatcher),
    ) { rows, covers ->
        val coversByPlaylist = covers.groupBy({ it.playlist_id }, { it.artwork_url!! })
        rows.map { row ->
            summaryOf(row.id, row.title, row.track_count, row.duration_ms, row.created_at, row.updated_at, coversByPlaylist[row.id].orEmpty())
        }
    }

    override fun observe(id: String): Flow<PlaylistDetails?> = combine(
        queries.selectSummary(id).asFlow().mapToOneOrNull(dispatcher),
        queries.selectEntries(id).asFlow().mapToList(dispatcher),
    ) { row, entries ->
        row ?: return@combine null
        val mapped = entries.map { entry ->
            PlaylistEntry(
                track = trackOf(MediaId.parse(entry.media_id), entry.title, entry.artists, entry.album, entry.duration_ms, entry.artwork_url),
                addedAt = entry.added_at,
            )
        }
        PlaylistDetails(
            summary = summaryOf(
                row.id, row.title, row.track_count, row.duration_ms, row.created_at, row.updated_at,
                mapped.mapNotNull { it.track.artwork?.url },
            ),
            entries = mapped,
        )
    }

    override fun observeContaining(trackIds: Collection<MediaId>): Flow<Map<String, Int>> {
        if (trackIds.isEmpty()) return flowOf(emptyMap())
        val keys = trackIds.map { it.toString() }.distinct()
        return queries.countContaining(keys).asFlow().mapToList(dispatcher).map { rows ->
            rows.associate { it.playlist_id to it.contained.toInt() }
        }
    }

    override suspend fun exists(id: String): Boolean = withContext(dispatcher) {
        queries.exists(id).executeAsOne() > 0
    }

    override suspend fun create(id: String, title: String, at: Long): Unit = withContext(dispatcher) {
        queries.insertPlaylist(id, title, at, at)
    }

    override suspend fun rename(id: String, title: String, at: Long): Unit = withContext(dispatcher) {
        queries.rename(title, at, id)
    }

    override suspend fun delete(id: String): Unit = withContext(dispatcher) {
        database.transaction {
            queries.deleteAllEntries(id)
            queries.deletePlaylist(id)
        }
    }

    override suspend fun contained(id: String, trackIds: Collection<MediaId>): Set<MediaId> = withContext(dispatcher) {
        if (trackIds.isEmpty()) return@withContext emptySet()
        queries.selectContained(id, trackIds.map { it.toString() }.distinct())
            .executeAsList()
            .mapTo(mutableSetOf()) { MediaId.parse(it) }
    }

    override suspend fun add(id: String, tracks: List<Track>, at: Long): Int = withContext(dispatcher) {
        database.transactionWithResult {
            val present = queries.selectContained(id, tracks.map { it.id.toString() }.distinct()).executeAsList().toMutableSet()
            val fresh = tracks.filter { present.add(it.id.toString()) }
            var position = (queries.maxPosition(id).executeAsOne().position ?: -1L) + 1
            fresh.forEach { track ->
                queries.insertEntry(
                    playlist_id = id,
                    media_id = track.id.toString(),
                    position = position++,
                    title = track.title,
                    artists = ArtistCodec.encode(track.artists.map { it.name }),
                    album = track.album?.title,
                    duration_ms = track.duration?.inWholeMilliseconds,
                    artwork_url = track.artwork?.url,
                    added_at = at,
                )
            }
            if (fresh.isNotEmpty()) queries.touch(at, id)
            fresh.size
        }
    }

    override suspend fun remove(id: String, trackIds: Collection<MediaId>, at: Long): Unit = withContext(dispatcher) {
        if (trackIds.isEmpty()) return@withContext
        database.transaction {
            queries.deleteEntries(id, trackIds.map { it.toString() }.distinct())
            queries.touch(at, id)
        }
    }

    private fun summaryOf(
        id: String,
        title: String,
        trackCount: Long,
        durationMs: Long,
        createdAt: Long,
        updatedAt: Long,
        covers: List<String>,
    ) = PlaylistSummary(
        id = id,
        title = title,
        trackCount = trackCount.toInt(),
        duration = durationMs.milliseconds,
        covers = covers.distinct().take(MAX_COVERS),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        const val MAX_COVERS = 4
    }
}
