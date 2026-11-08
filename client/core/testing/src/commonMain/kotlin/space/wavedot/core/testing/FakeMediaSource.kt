package space.wavedot.core.testing

import space.wavedot.core.contract.Catalog
import space.wavedot.core.contract.EntityProvider
import space.wavedot.core.contract.LyricsProvider
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.PagedQuery
import space.wavedot.core.contract.RemoteLibrary
import space.wavedot.core.contract.Search
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.contract.SourceDescriptor
import space.wavedot.core.contract.SourceKind
import space.wavedot.core.contract.Streaming
import space.wavedot.core.model.Album
import space.wavedot.core.model.Artist
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.Cursor
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Page
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
import space.wavedot.core.model.stream.StreamSource

class FakeMediaSource(
    id: SourceId = TestTracks.SOURCE,
    tracks: List<Track> = emptyList(),
    liked: List<Track> = emptyList(),
    private val searchFailure: Throwable? = null,
    withLibrary: Boolean = false,
    kind: SourceKind = SourceKind.STREAMING,
    override val lyrics: LyricsProvider? = null,
) : MediaSource {
    val tracks: MutableList<Track> = tracks.toMutableList()
    val liked: MutableList<Track> = liked.toMutableList()

    override val descriptor = SourceDescriptor(id, "Fake ${id.value}", kind)

    override val catalog: Catalog = object : Catalog {
        override val tracks: EntityProvider<Track> = object : EntityProvider<Track> {
            override suspend fun get(id: MediaId): Track? = this@FakeMediaSource.tracks.firstOrNull { it.id == id }
        }

        override val albumTracks: PagedQuery<MediaId, Track> = object : PagedQuery<MediaId, Track> {
            override suspend fun page(query: MediaId, cursor: Cursor?, limit: Int): Page<Track> =
                pageOf(this@FakeMediaSource.tracks.filter { it.album?.id == query }, cursor, limit)
        }

        override val artistTracks: PagedQuery<MediaId, Track> = object : PagedQuery<MediaId, Track> {
            override suspend fun page(query: MediaId, cursor: Cursor?, limit: Int): Page<Track> =
                pageOf(this@FakeMediaSource.tracks.filter { track -> track.artists.any { it.id == query } }, cursor, limit)
        }
    }

    override val search: Search = object : Search {
        override val tracks: PagedQuery<SearchQuery, Track> = object : PagedQuery<SearchQuery, Track> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<Track> {
                searchFailure?.let { throw it }
                val matches = this@FakeMediaSource.tracks.filter { it.title.contains(query.text, ignoreCase = true) }
                return pageOf(matches, cursor, limit)
            }
        }

        override val albums: PagedQuery<SearchQuery, Album> = object : PagedQuery<SearchQuery, Album> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<Album> {
                searchFailure?.let { throw it }
                val albums = this@FakeMediaSource.tracks
                    .mapNotNull { track -> track.album?.let { ref -> ref.id?.let { Album(it, ref.title, track.artists) } } }
                    .distinctBy { it.id }
                    .filter { it.title.contains(query.text, ignoreCase = true) }
                return pageOf(albums, cursor, limit)
            }
        }

        override val artists: PagedQuery<SearchQuery, Artist> = object : PagedQuery<SearchQuery, Artist> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<Artist> {
                searchFailure?.let { throw it }
                val artists = this@FakeMediaSource.tracks
                    .flatMap { it.artists }
                    .mapNotNull { ref -> ref.id?.let { Artist(it, ref.name) } }
                    .distinctBy { it.id }
                    .filter { it.name.contains(query.text, ignoreCase = true) }
                return pageOf(artists, cursor, limit)
            }
        }
    }

    override val streaming: Streaming = object : Streaming {
        override val supportedQualities: Set<AudioQuality> = setOf(AudioQuality.LOW, AudioQuality.HIGH)

        override suspend fun resolve(trackId: MediaId, quality: AudioQuality): StreamSource =
            StreamSource.Remote("https://fake.test/${trackId.key}?q=${quality.name}")
    }

    override val library: RemoteLibrary? = if (!withLibrary) null else object : RemoteLibrary {
        override val likedTracks: PagedQuery<Unit, Track> = object : PagedQuery<Unit, Track> {
            override suspend fun page(query: Unit, cursor: Cursor?, limit: Int): Page<Track> =
                pageOf(this@FakeMediaSource.liked, cursor, limit)
        }

        override suspend fun like(trackId: MediaId) {
            this@FakeMediaSource.tracks.firstOrNull { it.id == trackId }?.let { this@FakeMediaSource.liked += it }
        }

        override suspend fun unlike(trackId: MediaId) {
            this@FakeMediaSource.liked.removeAll { it.id == trackId }
        }
    }

    private fun <T> pageOf(items: List<T>, cursor: Cursor?, limit: Int): Page<T> {
        val start = cursor?.value?.toInt() ?: 0
        val end = (start + limit).coerceAtMost(items.size)
        val next = if (end < items.size) Cursor(end.toString()) else null
        return Page(items.subList(start, end), next)
    }
}
