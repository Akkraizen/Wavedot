package space.wavedot.core.model

import kotlin.time.Duration

sealed interface Entity {
    val id: MediaId
    val kind: EntityKind
}

data class Artwork(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

data class ArtistRef(val id: MediaId?, val name: String)

data class AlbumRef(val id: MediaId?, val title: String)

enum class Availability { AVAILABLE, REQUIRES_SUBSCRIPTION, UNAVAILABLE }

data class Track(
    override val id: MediaId,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val album: AlbumRef? = null,
    val duration: Duration? = null,
    val artwork: Artwork? = null,
    val explicit: Boolean = false,
    val availability: Availability = Availability.AVAILABLE,
) : Entity {
    override val kind: EntityKind get() = EntityKind.TRACK
    val artistLine: String get() = artists.joinToString(", ") { it.name }
}

data class Album(
    override val id: MediaId,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val year: Int? = null,
    val artwork: Artwork? = null,
    val trackCount: Int? = null,
) : Entity {
    override val kind: EntityKind get() = EntityKind.ALBUM
}

data class Artist(
    override val id: MediaId,
    val name: String,
    val artwork: Artwork? = null,
) : Entity {
    override val kind: EntityKind get() = EntityKind.ARTIST
}

data class Playlist(
    override val id: MediaId,
    val title: String,
    val owner: String? = null,
    val artwork: Artwork? = null,
    val trackCount: Int? = null,
) : Entity {
    override val kind: EntityKind get() = EntityKind.PLAYLIST
}
