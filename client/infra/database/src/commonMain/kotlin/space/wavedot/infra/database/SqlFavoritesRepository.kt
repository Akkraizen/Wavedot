package space.wavedot.infra.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import space.wavedot.core.domain.library.ChangeOrigin
import space.wavedot.core.domain.library.Favorite
import space.wavedot.core.domain.library.FavoriteChange
import space.wavedot.core.domain.library.FavoritesRepository
import space.wavedot.core.model.AlbumRef
import space.wavedot.infra.database.Favorite as FavoriteRow
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Artwork
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
import kotlin.time.Duration.Companion.milliseconds

class SqlFavoritesRepository(
    private val database: WavedotDatabase,
    private val dispatcher: CoroutineDispatcher,
) : FavoritesRepository {
    private val favorites get() = database.favoriteQueries
    private val changes get() = database.favoriteChangeQueries

    override fun observeAll(): Flow<List<Favorite>> =
        favorites.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeIds(): Flow<Set<MediaId>> =
        favorites.selectIds().asFlow().mapToList(dispatcher).map { rows ->
            rows.mapTo(mutableSetOf()) { MediaId(SourceId(it.source), it.media_key) }
        }

    override suspend fun isFavorite(id: MediaId): Boolean = withContext(dispatcher) {
        favorites.exists(id.source.value, id.key).executeAsOne() > 0
    }

    override suspend fun add(track: Track, at: Long, origin: ChangeOrigin): Unit = withContext(dispatcher) {
        database.transaction {
            favorites.upsert(
                source = track.id.source.value,
                media_key = track.id.key,
                title = track.title,
                artists = ArtistCodec.encode(track.artists.map { it.name }),
                album = track.album?.title,
                duration_ms = track.duration?.inWholeMilliseconds,
                artwork_url = track.artwork?.url,
                added_at = at,
            )
            if (origin == ChangeOrigin.LOCAL) changes.record(track.id.source.value, track.id.key, 0, at)
        }
    }

    override suspend fun remove(id: MediaId, at: Long, origin: ChangeOrigin): Unit = withContext(dispatcher) {
        database.transaction {
            favorites.delete(id.source.value, id.key)
            if (origin == ChangeOrigin.LOCAL) changes.record(id.source.value, id.key, 1, at)
        }
    }

    override suspend fun pendingChanges(): List<FavoriteChange> = withContext(dispatcher) {
        changes.selectPending().executeAsList().map { row ->
            val id = MediaId(SourceId(row.source), row.media_key)
            val favorite = if (row.deleted != 0L || row.title == null) null else Favorite(
                track = trackOf(id, row.title, row.artists.orEmpty(), row.album, row.duration_ms, row.artwork_url),
                addedAt = row.added_at ?: row.updated_at,
            )
            FavoriteChange(id, favorite, row.updated_at)
        }
    }

    override suspend fun markSynced(changes: List<FavoriteChange>): Unit = withContext(dispatcher) {
        database.transaction {
            changes.forEach { this@SqlFavoritesRepository.changes.clear(it.id.source.value, it.id.key, it.updatedAt) }
        }
    }

    override suspend fun applyRemote(changes: List<FavoriteChange>) {
        changes.forEach { change ->
            val favorite = change.favorite
            if (favorite == null) remove(change.id, change.updatedAt, ChangeOrigin.REMOTE)
            else add(favorite.track, favorite.addedAt, ChangeOrigin.REMOTE)
        }
    }

    private fun FavoriteRow.toDomain(): Favorite = Favorite(
        track = trackOf(MediaId(SourceId(source), media_key), title, artists, album, duration_ms, artwork_url),
        addedAt = added_at,
    )

    private fun trackOf(
        id: MediaId,
        title: String,
        artists: String,
        album: String?,
        durationMs: Long?,
        artworkUrl: String?,
    ): Track = Track(
        id = id,
        title = title,
        artists = ArtistCodec.decode(artists).map { ArtistRef(null, it) },
        album = album?.let { AlbumRef(null, it) },
        duration = durationMs?.milliseconds,
        artwork = artworkUrl?.let(::Artwork),
    )
}

internal object ArtistCodec {
    private const val SEPARATOR = '\u001F'

    fun encode(names: List<String>): String = names.joinToString(SEPARATOR.toString())

    fun decode(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(SEPARATOR)
}
