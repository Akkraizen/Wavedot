package space.wavedot.infra.database

import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Artwork
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import kotlin.time.Duration.Companion.milliseconds

internal fun trackOf(
    id: MediaId,
    title: String,
    artists: String,
    album: String?,
    durationMs: Long?,
    artworkUrl: String?,
): Track = Track(
    id = id,
    title = title,
    artists = ArtistCodec.decode(artists).map { ArtistRef(null, it) },
    album = album?.let { AlbumRef(null, it) },
    duration = durationMs?.milliseconds,
    artwork = artworkUrl?.let(::Artwork),
)

internal object ArtistCodec {
    private const val SEPARATOR = '\u001F'

    fun encode(names: List<String>): String = names.joinToString(SEPARATOR.toString())

    fun decode(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(SEPARATOR)
}
