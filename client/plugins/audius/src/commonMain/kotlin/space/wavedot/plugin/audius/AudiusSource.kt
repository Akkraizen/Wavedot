package space.wavedot.plugin.audius

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import space.wavedot.core.model.Artist
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Artwork
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.Availability
import space.wavedot.core.model.Cursor
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Page
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
import space.wavedot.core.model.stream.StreamSource
import kotlin.time.Duration.Companion.seconds

internal class AudiusSource(private val api: AudiusApi) : MediaSource {
    override val descriptor: SourceDescriptor = SourceDescriptor(SOURCE_ID, "Audius", SourceKind.STREAMING)

    override val catalog: Catalog = object : Catalog {
        override val tracks: EntityProvider<Track> = object : EntityProvider<Track> {
            override suspend fun get(id: MediaId): Track? =
                if (id.source == SOURCE_ID) api.track(id.key)?.let(::track) else null

            override suspend fun getMany(ids: List<MediaId>): List<Track> = coroutineScope {
                ids.map { async { get(it) } }.awaitAll().filterNotNull()
            }
        }

        override val albumTracks: PagedQuery<MediaId, Track> = object : PagedQuery<MediaId, Track> {
            override suspend fun page(query: MediaId, cursor: Cursor?, limit: Int): Page<Track> =
                Page(api.playlistTracks(query.key.removePrefix(PLAYLIST_PREFIX)).map(::track))
        }

        override val artistTracks: PagedQuery<MediaId, Track> = offsetQuery(
            fetch = { query: MediaId, offset, limit -> api.userTracks(query.key.removePrefix(ARTIST_PREFIX), offset, limit) },
            transform = { it.map(::track) },
        )
    }

    override val search: Search = object : Search {
        override val tracks: PagedQuery<SearchQuery, Track> = offsetQuery(
            fetch = { query: SearchQuery, offset, limit -> api.searchTracks(query.text, offset, limit) },
            transform = { tracks -> tracks.filter { it.isStreamable }.map(::track) },
        )

        override val albums: PagedQuery<SearchQuery, Album> = offsetQuery(
            fetch = { query: SearchQuery, offset, limit -> api.searchPlaylists(query.text, offset, limit) },
            transform = { it.map(::album) },
        )

        override val artists: PagedQuery<SearchQuery, Artist> = offsetQuery(
            fetch = { query: SearchQuery, offset, limit -> api.searchUsers(query.text, offset, limit) },
            transform = { it.map(::artist) },
        )
    }

    override val streaming: Streaming = object : Streaming {
        override val supportedQualities: Set<AudioQuality> = setOf(AudioQuality.HIGH)

        override suspend fun resolve(trackId: MediaId, quality: AudioQuality): StreamSource =
            StreamSource.Remote(api.streamUrl(trackId.key), mimeType = MIME_TYPE)
    }

    private fun <Q, R, T> offsetQuery(
        fetch: suspend (Q, Int, Int) -> List<R>,
        transform: (List<R>) -> List<T>,
    ): PagedQuery<Q, T> = object : PagedQuery<Q, T> {
        override suspend fun page(query: Q, cursor: Cursor?, limit: Int): Page<T> {
            val offset = cursor?.value?.toIntOrNull() ?: 0
            val raw = fetch(query, offset, limit)
            val next = if (raw.size >= limit) Cursor((offset + raw.size).toString()) else null
            return Page(transform(raw), next)
        }
    }

    companion object {
        val SOURCE_ID: SourceId = SourceId("audius")
        private const val PLAYLIST_PREFIX = "playlist/"
        private const val ARTIST_PREFIX = "user/"
        private const val MIME_TYPE = "audio/mpeg"

        internal fun track(track: AuTrack): Track = Track(
            id = MediaId(SOURCE_ID, track.id),
            title = track.title.trim(),
            artists = listOfNotNull(track.user?.let(::artistRef)),
            duration = track.duration?.takeIf { it > 0 }?.seconds,
            artwork = track.artwork?.best?.let(::Artwork),
            availability = if (track.isStreamable) Availability.AVAILABLE else Availability.UNAVAILABLE,
        )

        internal fun album(playlist: AuPlaylist): Album = Album(
            id = MediaId(SOURCE_ID, PLAYLIST_PREFIX + playlist.id),
            title = playlist.name.trim(),
            artists = listOfNotNull(playlist.user?.let(::artistRef)),
            artwork = playlist.artwork?.best?.let(::Artwork),
            trackCount = playlist.trackCount,
        )

        internal fun artist(user: AuUser): Artist = Artist(
            id = MediaId(SOURCE_ID, ARTIST_PREFIX + user.id),
            name = user.name.ifBlank { user.handle },
            artwork = user.profilePicture?.best?.let(::Artwork),
        )

        private fun artistRef(user: AuUser) = ArtistRef(MediaId(SOURCE_ID, ARTIST_PREFIX + user.id), user.name.ifBlank { user.handle })
    }
}
