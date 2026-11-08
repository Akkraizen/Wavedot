package space.wavedot.plugin.yandexmusic

import io.ktor.client.HttpClient
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
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
import space.wavedot.core.model.Artist
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.Cursor
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Page
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.plugin.yandexmusic.api.YandexMusicApi
import space.wavedot.plugin.yandexmusic.api.YmSearchSection
import space.wavedot.plugin.yandexmusic.stream.EncryptedStreamOpener

internal class YandexMusicSource(
    private val api: YandexMusicApi,
    private val http: HttpClient,
) : MediaSource {
    override val descriptor: SourceDescriptor = SourceDescriptor(SOURCE_ID, "Yandex Music", SourceKind.STREAMING)

    override val catalog: Catalog = object : Catalog {
        override val tracks: EntityProvider<Track> = object : EntityProvider<Track> {
            override suspend fun get(id: MediaId): Track? = getMany(listOf(id)).firstOrNull()

            override suspend fun getMany(ids: List<MediaId>): List<Track> =
                ids.filter { it.source == SOURCE_ID }
                    .chunked(BATCH_SIZE)
                    .flatMap { chunk -> api.tracks(chunk.map { it.key }) }
                    .map(YandexMusicMapper::track)
        }

        override val albumTracks: PagedQuery<MediaId, Track> = object : PagedQuery<MediaId, Track> {
            override suspend fun page(query: MediaId, cursor: Cursor?, limit: Int): Page<Track> {
                val album = api.albumWithTracks(YandexMusicMapper.rawId(query, YandexMusicMapper.ALBUM_PREFIX))
                return Page(album.volumes.flatten().map(YandexMusicMapper::track))
            }
        }

        override val artistTracks: PagedQuery<MediaId, Track> = object : PagedQuery<MediaId, Track> {
            override suspend fun page(query: MediaId, cursor: Cursor?, limit: Int): Page<Track> {
                val page = cursor?.value?.toIntOrNull() ?: 0
                val result = api.artistTracks(YandexMusicMapper.rawId(query, YandexMusicMapper.ARTIST_PREFIX), page, limit)
                val pager = result.pager
                val hasMore = pager != null && (page + 1) * pager.perPage < pager.total && result.tracks.isNotEmpty()
                return Page(result.tracks.map(YandexMusicMapper::track), if (hasMore) Cursor((page + 1).toString()) else null)
            }
        }
    }

    override val search: Search = object : Search {
        override val tracks: PagedQuery<SearchQuery, Track> = object : PagedQuery<SearchQuery, Track> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<Track> {
                val page = cursor?.value?.toIntOrNull() ?: 0
                val section = api.search(query.text, page)
                val consumed = (page + 1) * section.perPage.coerceAtLeast(section.results.size)
                val next = if (section.results.isNotEmpty() && consumed < section.total) Cursor((page + 1).toString()) else null
                return Page(section.results.take(limit).map(YandexMusicMapper::track), next)
            }
        }

        override val albums: PagedQuery<SearchQuery, Album> = object : PagedQuery<SearchQuery, Album> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<Album> {
                val page = cursor?.value?.toIntOrNull() ?: 0
                val section = api.searchAlbums(query.text, page)
                return Page(section.results.take(limit).map(YandexMusicMapper::album), nextPage(page, section))
            }
        }

        override val artists: PagedQuery<SearchQuery, Artist> = object : PagedQuery<SearchQuery, Artist> {
            override suspend fun page(query: SearchQuery, cursor: Cursor?, limit: Int): Page<Artist> {
                val page = cursor?.value?.toIntOrNull() ?: 0
                val section = api.searchArtists(query.text, page)
                return Page(section.results.take(limit).map(YandexMusicMapper::artist), nextPage(page, section))
            }
        }
    }

    override val streaming: Streaming = object : Streaming {
        override val supportedQualities: Set<AudioQuality> =
            setOf(AudioQuality.LOW, AudioQuality.NORMAL, AudioQuality.LOSSLESS)

        override suspend fun resolve(trackId: MediaId, quality: AudioQuality): StreamSource {
            val info = api.fileInfo(trackId.key, quality.toApi())
            val url = info.urls.firstOrNull() ?: info.url
                ?: throw WavedotException(WavedotError.NotFound("stream for $trackId"))
            val mime = mimeTypeFor(info.codec)
            val key = info.key
            return if (key.isNullOrBlank()) StreamSource.Remote(url, mimeType = mime)
            else StreamSource.Custom(mime, EncryptedStreamOpener(http, url, key))
        }
    }

    override val library: RemoteLibrary = object : RemoteLibrary {
        override val likedTracks: PagedQuery<Unit, Track> = object : PagedQuery<Unit, Track> {
            override suspend fun page(query: Unit, cursor: Cursor?, limit: Int): Page<Track> {
                val ids = api.likedTrackIds()
                val start = cursor?.value?.toIntOrNull() ?: 0
                val end = (start + limit).coerceAtMost(ids.size)
                val slice = ids.subList(start.coerceAtMost(end), end).map { YandexMusicMapper.trackId(it.id) }
                val next = if (end < ids.size) Cursor(end.toString()) else null
                return Page(catalog.tracks.getMany(slice), next)
            }
        }

        override suspend fun like(trackId: MediaId) = api.setLiked(listOf(trackId.key), liked = true)

        override suspend fun unlike(trackId: MediaId) = api.setLiked(listOf(trackId.key), liked = false)
    }

    private fun nextPage(page: Int, section: YmSearchSection<*>): Cursor? {
        val consumed = (page + 1) * section.perPage.coerceAtLeast(section.results.size)
        return if (section.results.isNotEmpty() && consumed < section.total) Cursor((page + 1).toString()) else null
    }

    companion object {
        val SOURCE_ID: SourceId = SourceId("yandex-music")
        private const val BATCH_SIZE = 100

        internal fun AudioQuality.toApi(): String = when (this) {
            AudioQuality.LOW -> "lq"
            AudioQuality.NORMAL, AudioQuality.HIGH -> "nq"
            AudioQuality.LOSSLESS -> "lossless"
        }

        internal fun mimeTypeFor(codec: String): String = when (codec) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "aac", "he-aac" -> "audio/aac"
            else -> "audio/mp4"
        }
    }
}
