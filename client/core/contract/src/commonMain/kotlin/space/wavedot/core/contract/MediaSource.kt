package space.wavedot.core.contract

import space.wavedot.core.model.Album
import space.wavedot.core.model.Artist
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.EntityKind
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Playlist
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
import space.wavedot.core.model.stream.StreamSource

data class SourceDescriptor(
    val id: SourceId,
    val displayName: String,
    val kind: SourceKind,
)

enum class SourceKind { LOCAL, STREAMING }

interface MediaSource {
    val descriptor: SourceDescriptor
    val catalog: Catalog? get() = null
    val search: Search? get() = null
    val streaming: Streaming? get() = null
    val library: RemoteLibrary? get() = null
}

interface Catalog {
    val tracks: EntityProvider<Track>
    val albums: EntityProvider<Album>? get() = null
    val artists: EntityProvider<Artist>? get() = null
    val playlists: EntityProvider<Playlist>? get() = null
    val albumTracks: PagedQuery<MediaId, Track>? get() = null
    val playlistTracks: PagedQuery<MediaId, Track>? get() = null
}

data class SearchQuery(
    val text: String,
    val kinds: Set<EntityKind> = setOf(EntityKind.TRACK),
)

interface Search {
    val tracks: PagedQuery<SearchQuery, Track>
    val albums: PagedQuery<SearchQuery, Album>? get() = null
    val artists: PagedQuery<SearchQuery, Artist>? get() = null
    val playlists: PagedQuery<SearchQuery, Playlist>? get() = null
}

interface Streaming {
    val supportedQualities: Set<AudioQuality>

    suspend fun resolve(trackId: MediaId, quality: AudioQuality): StreamSource
}

interface RemoteLibrary {
    val likedTracks: PagedQuery<Unit, Track>

    suspend fun like(trackId: MediaId)

    suspend fun unlike(trackId: MediaId)
}
