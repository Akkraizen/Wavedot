package space.wavedot.plugin.yandexmusic

import space.wavedot.core.model.Album
import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.Artist
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Artwork
import space.wavedot.core.model.Availability
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import space.wavedot.plugin.yandexmusic.api.YmAlbum
import space.wavedot.plugin.yandexmusic.api.YmArtist
import space.wavedot.plugin.yandexmusic.api.YmTrack
import kotlin.time.Duration.Companion.milliseconds

internal object YandexMusicMapper {
    fun track(dto: YmTrack): Track {
        val album = dto.albums.firstOrNull()
        val baseTitle = dto.title.orEmpty().ifBlank { "Unknown" }
        return Track(
            id = trackId(dto.id.content),
            title = dto.version?.takeIf { it.isNotBlank() }?.let { "$baseTitle ($it)" } ?: baseTitle,
            artists = dto.artists.map { artist ->
                ArtistRef(artist.id?.content?.let(::artistId), artist.name)
            },
            album = album?.let { AlbumRef(it.id?.content?.let(::albumId), it.title) },
            duration = dto.durationMs?.milliseconds,
            artwork = (dto.coverUri ?: album?.coverUri)?.let(::artwork),
            explicit = dto.contentWarning == "explicit",
            availability = if (dto.available) Availability.AVAILABLE else Availability.UNAVAILABLE,
        )
    }

    fun album(dto: YmAlbum): Album = Album(
        id = albumId(dto.id?.content.orEmpty()),
        title = dto.title.ifBlank { "Unknown album" },
        artists = dto.artists.map { ArtistRef(it.id?.content?.let(::artistId), it.name) },
        year = dto.year,
        artwork = dto.coverUri?.let(::artwork),
        trackCount = dto.trackCount,
    )

    fun artist(dto: YmArtist): Artist = Artist(
        id = artistId(dto.id?.content.orEmpty()),
        name = dto.name.ifBlank { "Unknown artist" },
        artwork = (dto.cover?.uri ?: dto.ogImage)?.let(::artwork),
    )

    fun albumId(raw: String): MediaId = MediaId(YandexMusicSource.SOURCE_ID, "$ALBUM_PREFIX$raw")

    fun artistId(raw: String): MediaId = MediaId(YandexMusicSource.SOURCE_ID, "$ARTIST_PREFIX$raw")

    fun rawId(id: MediaId, prefix: String): String = id.key.removePrefix(prefix)

    const val ALBUM_PREFIX: String = "album/"
    const val ARTIST_PREFIX: String = "artist/"

    fun trackId(raw: String): MediaId = MediaId(YandexMusicSource.SOURCE_ID, raw.substringBefore(':'))

    fun artwork(coverUri: String, size: Int = 400): Artwork =
        Artwork(
            url = (if (coverUri.startsWith("http")) coverUri else "https://$coverUri").replace("%%", "${size}x$size"),
            width = size,
            height = size,
        )
}
