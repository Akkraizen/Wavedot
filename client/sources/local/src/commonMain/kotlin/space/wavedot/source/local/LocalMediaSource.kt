package space.wavedot.source.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.wavedot.core.contract.Catalog
import space.wavedot.core.contract.EntityProvider
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.PagedQuery
import space.wavedot.core.contract.Search
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.contract.SourceDescriptor
import space.wavedot.core.contract.SourceKind
import space.wavedot.core.contract.Streaming
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.Cursor
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Page
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
import space.wavedot.core.model.stream.StreamSource

data class LocalTrack(
    val track: Track,
    val location: String,
    val mimeType: String? = null,
)

interface LocalTrackScanner {
    suspend fun scan(): List<LocalTrack>
}

class LocalMediaSource(private val scanner: LocalTrackScanner) : MediaSource {
    private val index = MutableStateFlow<Map<MediaId, LocalTrack>>(emptyMap())

    val tracks: StateFlow<Map<MediaId, LocalTrack>> = index.asStateFlow()

    override val descriptor: SourceDescriptor = SourceDescriptor(SOURCE_ID, "Local files", SourceKind.LOCAL)

    suspend fun refresh(): Int {
        val scanned = scanner.scan()
        index.value = scanned.associateBy { it.track.id }
        return scanned.size
    }

    fun allTracks(): List<Track> = index.value.values.map { it.track }.sortedWith(compareBy({ it.artistLine }, { it.title }))

    override val catalog: Catalog = object : Catalog {
        override val tracks: EntityProvider<Track> = object : EntityProvider<Track> {
            override suspend fun get(id: MediaId): Track? = index.value[id]?.track
        }
    }

    override val search: Search = object : Search {
        override val tracks: PagedQuery<SearchQuery, Track> = object : PagedQuery<SearchQuery, Track> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<Track> {
                val needle = query.text.trim()
                val matches = allTracks().filter { track ->
                    track.title.contains(needle, ignoreCase = true) ||
                        track.artistLine.contains(needle, ignoreCase = true) ||
                        track.album?.title?.contains(needle, ignoreCase = true) == true
                }
                val start = cursor?.value?.toIntOrNull() ?: 0
                val end = (start + limit).coerceAtMost(matches.size)
                val next = if (end < matches.size) Cursor(end.toString()) else null
                return Page(matches.subList(start.coerceAtMost(end), end), next)
            }
        }
    }

    override val streaming: Streaming = object : Streaming {
        override val supportedQualities: Set<AudioQuality> = emptySet()

        override suspend fun resolve(trackId: MediaId, quality: AudioQuality): StreamSource {
            val local = index.value[trackId] ?: throw WavedotException(WavedotError.NotFound("local track $trackId"))
            return StreamSource.LocalFile(local.location, local.mimeType)
        }
    }

    companion object {
        val SOURCE_ID: SourceId = SourceId("local")

        fun idFor(location: String): MediaId = MediaId(SOURCE_ID, location)
    }
}
