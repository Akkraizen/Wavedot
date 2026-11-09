package space.wavedot.core.domain.library

import space.wavedot.core.common.TimeProvider
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.model.Track
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PlaylistEditor(
    private val playlists: PlaylistRepository,
    private val time: TimeProvider,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    suspend fun create(title: String, tracks: List<Track> = emptyList()): String {
        val id = newId()
        val now = time.nowMillis()
        playlists.create(id, normalize(title) ?: DEFAULT_TITLE, now)
        if (tracks.isNotEmpty()) playlists.add(id, tracks.distinctBy { it.id }, now)
        return id
    }

    suspend fun rename(id: String, title: String) {
        val normalized = normalize(title) ?: return
        requireExists(id)
        playlists.rename(id, normalized, time.nowMillis())
    }

    suspend fun delete(id: String) {
        playlists.delete(id)
    }

    suspend fun add(id: String, tracks: List<Track>): Int {
        requireExists(id)
        val missing = missing(id, tracks)
        if (missing.isEmpty()) return 0
        return playlists.add(id, missing, time.nowMillis())
    }

    suspend fun remove(id: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        playlists.remove(id, tracks.map { it.id }.toSet(), time.nowMillis())
    }

    suspend fun toggle(id: String, tracks: List<Track>): Boolean {
        requireExists(id)
        val missing = missing(id, tracks)
        return if (missing.isEmpty()) {
            remove(id, tracks)
            false
        } else {
            playlists.add(id, missing, time.nowMillis())
            true
        }
    }

    private suspend fun missing(id: String, tracks: List<Track>): List<Track> {
        val unique = tracks.distinctBy { it.id }
        val present = playlists.contained(id, unique.map { it.id })
        return unique.filter { it.id !in present }
    }

    private suspend fun requireExists(id: String) {
        if (!playlists.exists(id)) throw WavedotException(WavedotError.NotFound("playlist $id"))
    }

    private fun normalize(title: String): String? =
        title.trim().replace(WHITESPACE, " ").take(MAX_TITLE_LENGTH).ifEmpty { null }

    companion object {
        const val DEFAULT_TITLE: String = "New playlist"
        const val MAX_TITLE_LENGTH: Int = 100
        private val WHITESPACE = Regex("\\s+")
    }
}
