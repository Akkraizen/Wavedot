package space.wavedot.plugin.yandexmusic.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class YmResponse<T>(
    val result: T? = null,
    val error: YmError? = null,
)

@Serializable
internal data class YmError(
    val name: String? = null,
    val message: String? = null,
)

@Serializable
internal data class YmTrack(
    val id: JsonPrimitive,
    val title: String? = null,
    val version: String? = null,
    val artists: List<YmArtist> = emptyList(),
    val albums: List<YmAlbum> = emptyList(),
    val durationMs: Long? = null,
    val coverUri: String? = null,
    val available: Boolean = true,
    val contentWarning: String? = null,
)

@Serializable
internal data class YmArtist(
    val id: JsonPrimitive? = null,
    val name: String = "",
    val cover: YmCover? = null,
    val ogImage: String? = null,
)

@Serializable
internal data class YmCover(
    val uri: String? = null,
)

@Serializable
internal data class YmAlbum(
    val id: JsonPrimitive? = null,
    val title: String = "",
    val year: Int? = null,
    val coverUri: String? = null,
    val artists: List<YmArtist> = emptyList(),
    val trackCount: Int? = null,
    val volumes: List<List<YmTrack>> = emptyList(),
)

@Serializable
internal data class YmSearch(
    val tracks: YmSearchSection<YmTrack>? = null,
    val albums: YmSearchSection<YmAlbum>? = null,
    val artists: YmSearchSection<YmArtist>? = null,
)

@Serializable
internal data class YmArtistTracks(
    val tracks: List<YmTrack> = emptyList(),
    val pager: YmPager? = null,
)

@Serializable
internal data class YmPager(
    val page: Int = 0,
    val perPage: Int = 0,
    val total: Int = 0,
)

@Serializable
internal data class YmSearchSection<T>(
    val total: Int = 0,
    val perPage: Int = 0,
    val results: List<T> = emptyList(),
)

@Serializable
internal data class YmAccountStatus(
    val account: YmAccount = YmAccount(),
)

@Serializable
internal data class YmAccount(
    val uid: Long? = null,
)

@Serializable
internal data class YmLikes(
    val library: YmLibrary = YmLibrary(),
)

@Serializable
internal data class YmLibrary(
    val tracks: List<YmTrackShort> = emptyList(),
)

@Serializable
internal data class YmTrackShort(
    val id: String,
    val albumId: String? = null,
)

@Serializable
internal data class YmFileInfo(
    val downloadInfo: YmDownloadInfo,
)

@Serializable
internal data class YmDownloadInfo(
    val trackId: String? = null,
    val quality: String = "",
    val codec: String = "",
    val urls: List<String> = emptyList(),
    val url: String? = null,
    val key: String? = null,
    val bitrate: Int? = null,
    val transport: String? = null,
)

@Serializable
internal data class YmTrackLyrics(
    val downloadUrl: String,
    val lyricId: Long? = null,
    val externalLyricId: String? = null,
    val writers: List<String> = emptyList(),
    val major: YmLyricsMajor? = null,
)

@Serializable
internal data class YmLyricsMajor(
    val id: Int? = null,
    val name: String? = null,
    val prettyName: String? = null,
)
