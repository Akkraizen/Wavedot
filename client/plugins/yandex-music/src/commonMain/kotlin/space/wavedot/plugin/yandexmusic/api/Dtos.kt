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
)

@Serializable
internal data class YmAlbum(
    val id: JsonPrimitive? = null,
    val title: String = "",
    val year: Int? = null,
    val coverUri: String? = null,
)

@Serializable
internal data class YmSearch(
    val tracks: YmSearchSection<YmTrack>? = null,
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
