package space.wavedot.plugin.radiobrowser

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
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Artwork
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.Cursor
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Page
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
import space.wavedot.core.model.stream.StreamSource

internal class RadioBrowserSource(private val api: RadioBrowserApi) : MediaSource {
    override val descriptor: SourceDescriptor = SourceDescriptor(SOURCE_ID, "Internet radio", SourceKind.RADIO)

    override val catalog: Catalog = object : Catalog {
        override val tracks: EntityProvider<Track> = object : EntityProvider<Track> {
            override suspend fun get(id: MediaId): Track? = getMany(listOf(id)).firstOrNull()

            override suspend fun getMany(ids: List<MediaId>): List<Track> =
                api.stations(ids.filter { it.source == SOURCE_ID }.map { it.key }).map(::track)
        }
    }

    override val search: Search = object : Search {
        override val tracks: PagedQuery<SearchQuery, Track> = object : PagedQuery<SearchQuery, Track> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<Track> {
                val offset = cursor?.value?.toIntOrNull() ?: 0
                val stations = api.search(query.text, offset, limit)
                val next = if (stations.size >= limit) Cursor((offset + stations.size).toString()) else null
                return Page(stations.map(::track), next)
            }
        }
    }

    override val streaming: Streaming = object : Streaming {
        override val supportedQualities: Set<AudioQuality> = setOf(AudioQuality.NORMAL)

        override suspend fun resolve(trackId: MediaId, quality: AudioQuality): StreamSource {
            val resolved = api.streamUrl(trackId.key).takeIf { it.ok }?.url?.takeIf { it.isNotBlank() }
            val station = if (resolved == null) api.stations(listOf(trackId.key)).firstOrNull() else null
            val url = resolved ?: station?.playableUrl()
                ?: throw WavedotException(WavedotError.NotFound("stream for station ${trackId.key}"))
            return StreamSource.Remote(url, mimeType = station?.let { mimeTypeFor(it.codec) })
        }
    }

    companion object {
        val SOURCE_ID: SourceId = SourceId("radio")

        internal fun track(station: RbStation): Track = Track(
            id = MediaId(SOURCE_ID, station.stationUuid),
            title = station.name.trim().ifEmpty { "Unnamed station" },
            artists = listOfNotNull(station.subtitle()?.let { ArtistRef(null, it) }),
            artwork = station.favicon.takeIf { it.startsWith("http") }?.let(::Artwork),
        )

        internal fun mimeTypeFor(codec: String): String? = when (codec.uppercase()) {
            "MP3" -> "audio/mpeg"
            "AAC", "AAC+" -> "audio/aac"
            "OGG" -> "audio/ogg"
            "FLAC" -> "audio/flac"
            else -> null
        }

        private fun RbStation.playableUrl(): String? = urlResolved.ifBlank { url }.ifBlank { null }

        private fun RbStation.subtitle(): String? {
            val genres = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ")
            val format = listOfNotNull(codec.ifBlank { null }, bitrate.takeIf { it > 0 }?.let { "$it kbps" }).joinToString(" ")
            return listOf(country.trim(), genres, format).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
        }
    }
}
