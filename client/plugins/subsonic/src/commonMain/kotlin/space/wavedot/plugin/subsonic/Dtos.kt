package space.wavedot.plugin.subsonic

import kotlinx.serialization.Serializable

@Serializable
internal data class SsError(
    val code: Int = 0,
    val message: String = "",
)

@Serializable
internal data class SsSong(
    val id: String,
    val title: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val duration: Long? = null,
    val coverArt: String? = null,
    val contentType: String? = null,
)

@Serializable
internal data class SsAlbum(
    val id: String,
    val name: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val year: Int? = null,
    val song: List<SsSong> = emptyList(),
) {
    val displayName: String get() = name ?: title ?: ""
}

@Serializable
internal data class SsArtist(
    val id: String,
    val name: String = "",
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val album: List<SsAlbum> = emptyList(),
)

@Serializable
internal data class SsSearchResult(
    val song: List<SsSong> = emptyList(),
    val album: List<SsAlbum> = emptyList(),
    val artist: List<SsArtist> = emptyList(),
)

@Serializable
internal data class SsStarred(
    val song: List<SsSong> = emptyList(),
)

@Serializable
internal data class SsSongResponse(val song: SsSong)

@Serializable
internal data class SsAlbumResponse(val album: SsAlbum)

@Serializable
internal data class SsArtistResponse(val artist: SsArtist)

@Serializable
internal data class SsSearchResponse(val searchResult3: SsSearchResult = SsSearchResult())

@Serializable
internal data class SsStarredResponse(val starred2: SsStarred = SsStarred())
