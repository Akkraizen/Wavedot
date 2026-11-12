package space.wavedot.plugin.subsonic

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.contract.ArtworkResolver
import space.wavedot.core.contract.Catalog
import space.wavedot.core.contract.EntityProvider
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.PagedQuery
import space.wavedot.core.contract.RemoteLibrary
import space.wavedot.core.contract.Search
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.contract.SourceDescriptor
import space.wavedot.core.contract.SourceKind
import space.wavedot.core.contract.Streaming
import space.wavedot.core.model.Album
import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.Artist
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Artwork
import space.wavedot.core.model.ArtworkReference
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.Cursor
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Page
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
import space.wavedot.core.model.stream.StreamSource
import kotlin.time.Duration.Companion.seconds

internal class SubsonicSource(
    private val api: SubsonicApi,
    displayName: String,
) : MediaSource {
    override val descriptor: SourceDescriptor = SourceDescriptor(SOURCE_ID, displayName, SourceKind.STREAMING)

    override val artwork: ArtworkResolver = ArtworkResolver { key -> api.coverArtUrl(key) }

    override val catalog: Catalog = object : Catalog {
        override val tracks: EntityProvider<Track> = object : EntityProvider<Track> {
            override suspend fun get(id: MediaId): Track? =
                if (id.source == SOURCE_ID) api.song(id.key)?.let(::track) else null

            override suspend fun getMany(ids: List<MediaId>): List<Track> = coroutineScope {
                ids.map { async { get(it) } }.awaitAll().filterNotNull()
            }
        }

        override val albumTracks: PagedQuery<MediaId, Track> = object : PagedQuery<MediaId, Track> {
            override suspend fun page(query: MediaId, cursor: Cursor?, limit: Int): Page<Track> {
                val album = api.album(query.key.removePrefix(ALBUM_PREFIX))
                    ?: throw WavedotException(WavedotError.NotFound("album ${query.key}"))
                return Page(album.song.map(::track))
            }
        }

        override val artistTracks: PagedQuery<MediaId, Track> = object : PagedQuery<MediaId, Track> {
            override suspend fun page(query: MediaId, cursor: Cursor?, limit: Int): Page<Track> {
                val artist = api.artist(query.key.removePrefix(ARTIST_PREFIX))
                    ?: throw WavedotException(WavedotError.NotFound("artist ${query.key}"))
                val start = cursor?.value?.toIntOrNull() ?: 0
                val albums = artist.album.drop(start).take(ALBUMS_PER_PAGE)
                val tracks = coroutineScope { albums.map { async { api.album(it.id)?.song.orEmpty() } }.awaitAll() }
                val next = (start + albums.size).takeIf { albums.isNotEmpty() && it < artist.album.size }
                return Page(tracks.flatten().map(::track), next?.let { Cursor(it.toString()) })
            }
        }
    }

    override val search: Search = object : Search {
        override val tracks: PagedQuery<SearchQuery, Track> = offsetQuery { text, offset, limit ->
            api.search(text, songs = limit to offset).song.map(::track)
        }

        override val albums: PagedQuery<SearchQuery, Album> = offsetQuery { text, offset, limit ->
            api.search(text, albums = limit to offset).album.map(::album)
        }

        override val artists: PagedQuery<SearchQuery, Artist> = offsetQuery { text, offset, limit ->
            api.search(text, artists = limit to offset).artist.map(::artist)
        }
    }

    override val streaming: Streaming = object : Streaming {
        override val supportedQualities: Set<AudioQuality> = AudioQuality.entries.toSet()

        override suspend fun resolve(trackId: MediaId, quality: AudioQuality): StreamSource =
            StreamSource.Remote(api.streamUrl(trackId.key, quality.maxBitRate()))
    }

    override val library: RemoteLibrary = object : RemoteLibrary {
        override val likedTracks: PagedQuery<Unit, Track> = object : PagedQuery<Unit, Track> {
            override suspend fun page(query: Unit, cursor: Cursor?, limit: Int): Page<Track> {
                val songs = api.starredSongs()
                val start = cursor?.value?.toIntOrNull() ?: 0
                val end = (start + limit).coerceAtMost(songs.size)
                val next = if (end < songs.size) Cursor(end.toString()) else null
                return Page(songs.subList(start.coerceAtMost(end), end).map(::track), next)
            }
        }

        override suspend fun like(trackId: MediaId) = api.star(trackId.key)

        override suspend fun unlike(trackId: MediaId) = api.unstar(trackId.key)
    }

    private fun <T> offsetQuery(fetch: suspend (String, Int, Int) -> List<T>): PagedQuery<SearchQuery, T> =
        object : PagedQuery<SearchQuery, T> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<T> {
                val offset = cursor?.value?.toIntOrNull() ?: 0
                val items = fetch(query.text, offset, limit)
                val next = if (items.size >= limit) Cursor((offset + items.size).toString()) else null
                return Page(items, next)
            }
        }

    private fun track(song: SsSong): Track = Track(
        id = MediaId(SOURCE_ID, song.id),
        title = song.title,
        artists = listOfNotNull(song.artist?.let { ArtistRef(song.artistId?.let { id -> MediaId(SOURCE_ID, ARTIST_PREFIX + id) }, it) }),
        album = song.album?.let { AlbumRef(song.albumId?.let { id -> MediaId(SOURCE_ID, ALBUM_PREFIX + id) }, it) },
        duration = song.duration?.takeIf { it > 0 }?.seconds,
        artwork = song.coverArt?.let(::cover),
    )

    private fun album(album: SsAlbum): Album = Album(
        id = MediaId(SOURCE_ID, ALBUM_PREFIX + album.id),
        title = album.displayName,
        artists = listOfNotNull(album.artist?.let { ArtistRef(album.artistId?.let { id -> MediaId(SOURCE_ID, ARTIST_PREFIX + id) }, it) }),
        year = album.year,
        artwork = album.coverArt?.let(::cover),
        trackCount = album.songCount,
    )

    private fun artist(artist: SsArtist): Artist = Artist(
        id = MediaId(SOURCE_ID, ARTIST_PREFIX + artist.id),
        name = artist.name,
        artwork = artist.artistImageUrl?.takeIf { it.isNotBlank() }?.let(::Artwork) ?: artist.coverArt?.let(::cover),
    )

    private fun cover(id: String): Artwork = Artwork(ArtworkReference.of(SOURCE_ID, id))

    companion object {
        val SOURCE_ID: SourceId = SourceId("subsonic")
        private const val ALBUM_PREFIX = "album/"
        private const val ARTIST_PREFIX = "artist/"
        private const val ALBUMS_PER_PAGE = 10

        internal fun AudioQuality.maxBitRate(): Int? = when (this) {
            AudioQuality.LOW -> 128
            AudioQuality.NORMAL -> 192
            AudioQuality.HIGH -> 320
            AudioQuality.LOSSLESS -> null
        }
    }
}
