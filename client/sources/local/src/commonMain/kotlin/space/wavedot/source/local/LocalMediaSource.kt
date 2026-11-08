package space.wavedot.source.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.contract.Catalog
import space.wavedot.core.contract.EntityProvider
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.PagedQuery
import space.wavedot.core.contract.Search
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.contract.SourceDescriptor
import space.wavedot.core.contract.SourceKind
import space.wavedot.core.contract.Streaming
import space.wavedot.core.model.Album
import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.Artist
import space.wavedot.core.model.ArtistRef
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
        val scanned = scanner.scan().map { it.copy(track = withReferences(it.track)) }
        index.value = scanned.associateBy { it.track.id }
        return scanned.size
    }

    fun allTracks(): List<Track> =
        index.value.values
            .map { it.track }
            .sortedWith(compareBy({ it.artistLine }, { it.album?.title.orEmpty() }, { it.title }))

    fun albums(): List<Album> =
        allTracks()
            .filter { it.album?.id != null }
            .groupBy { it.album!!.id!! }
            .map { (id, tracks) ->
                val first = tracks.first()
                Album(
                    id = id,
                    title = first.album!!.title,
                    artists = first.artists.take(1),
                    artwork = tracks.firstNotNullOfOrNull { it.artwork },
                    trackCount = tracks.size,
                )
            }
            .sortedWith(compareBy({ it.artists.firstOrNull()?.name.orEmpty() }, { it.title }))

    fun artists(): List<Artist> =
        allTracks()
            .flatMap { track -> track.artists.map { it to track } }
            .filter { it.first.id != null }
            .groupBy { it.first.id!! }
            .map { (id, entries) ->
                Artist(id = id, name = entries.first().first.name, artwork = entries.firstNotNullOfOrNull { it.second.artwork })
            }
            .sortedBy { it.name.lowercase() }

    override val catalog: Catalog = object : Catalog {
        override val tracks: EntityProvider<Track> = object : EntityProvider<Track> {
            override suspend fun get(id: MediaId): Track? = index.value[id]?.track
        }

        override val albumTracks: PagedQuery<MediaId, Track> = object : PagedQuery<MediaId, Track> {
            override suspend fun page(query: MediaId, cursor: Cursor?, limit: Int): Page<Track> =
                pageOf(allTracks().filter { it.album?.id == query }, cursor, limit)
        }

        override val artistTracks: PagedQuery<MediaId, Track> = object : PagedQuery<MediaId, Track> {
            override suspend fun page(query: MediaId, cursor: Cursor?, limit: Int): Page<Track> =
                pageOf(allTracks().filter { track -> track.artists.any { it.id == query } }, cursor, limit)
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
                return pageOf(matches, cursor, limit)
            }
        }

        override val albums: PagedQuery<SearchQuery, Album> = object : PagedQuery<SearchQuery, Album> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<Album> {
                val needle = query.text.trim()
                val matches = albums().filter { album ->
                    album.title.contains(needle, ignoreCase = true) ||
                        album.artists.any { it.name.contains(needle, ignoreCase = true) }
                }
                return pageOf(matches, cursor, limit)
            }
        }

        override val artists: PagedQuery<SearchQuery, Artist> = object : PagedQuery<SearchQuery, Artist> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<Artist> {
                val needle = query.text.trim()
                return pageOf(artists().filter { it.name.contains(needle, ignoreCase = true) }, cursor, limit)
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

    private fun withReferences(track: Track): Track {
        val artists = track.artists.map { it.copy(id = it.id ?: artistId(it.name)) }
        val album = track.album?.let { ref ->
            AlbumRef(ref.id ?: albumId(ref.title, artists.firstOrNull()?.name.orEmpty()), ref.title)
        }
        return track.copy(artists = artists, album = album)
    }

    private fun <T> pageOf(items: List<T>, cursor: Cursor?, limit: Int): Page<T> {
        val start = (cursor?.value?.toIntOrNull() ?: 0).coerceAtMost(items.size)
        val end = (start + limit).coerceAtMost(items.size)
        val next = if (end < items.size) Cursor(end.toString()) else null
        return Page(items.subList(start, end), next)
    }

    companion object {
        val SOURCE_ID: SourceId = SourceId("local")

        fun idFor(location: String): MediaId = MediaId(SOURCE_ID, location)

        fun artistId(name: String): MediaId = MediaId(SOURCE_ID, "artist/${name.trim().lowercase()}")

        fun albumId(title: String, artist: String): MediaId =
            MediaId(SOURCE_ID, "album/${artist.trim().lowercase()}/${title.trim().lowercase()}")

        fun artistRef(name: String): ArtistRef = ArtistRef(artistId(name), name)
    }
}
