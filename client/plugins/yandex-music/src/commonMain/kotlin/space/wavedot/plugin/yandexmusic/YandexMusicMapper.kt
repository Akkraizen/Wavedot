package space.wavedot.plugin.yandexmusic

import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Artwork
import space.wavedot.core.model.Availability
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
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
                ArtistRef(artist.id?.content?.let { MediaId(YandexMusicSource.SOURCE_ID, "artist/$it") }, artist.name)
            },
            album = album?.let { AlbumRef(it.id?.content?.let { id -> MediaId(YandexMusicSource.SOURCE_ID, "album/$id") }, it.title) },
            duration = dto.durationMs?.milliseconds,
            artwork = (dto.coverUri ?: album?.coverUri)?.let(::artwork),
            explicit = dto.contentWarning == "explicit",
            availability = if (dto.available) Availability.AVAILABLE else Availability.UNAVAILABLE,
        )
    }

    fun trackId(raw: String): MediaId = MediaId(YandexMusicSource.SOURCE_ID, raw.substringBefore(':'))

    fun artwork(coverUri: String, size: Int = 400): Artwork =
        Artwork("https://" + coverUri.replace("%%", "${size}x$size"), size, size)
}
