package space.wavedot.plugin.audius

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AuResponse<T>(val data: T)

@Serializable
internal data class AuArtwork(
    @SerialName("150x150") val small: String? = null,
    @SerialName("480x480") val medium: String? = null,
    @SerialName("1000x1000") val large: String? = null,
) {
    val best: String? get() = medium ?: large ?: small
}

@Serializable
internal data class AuUser(
    val id: String,
    val name: String = "",
    val handle: String = "",
    @SerialName("profile_picture") val profilePicture: AuArtwork? = null,
)

@Serializable
internal data class AuTrack(
    val id: String,
    val title: String = "",
    val duration: Long? = null,
    val user: AuUser? = null,
    val artwork: AuArtwork? = null,
    @SerialName("is_streamable") val isStreamable: Boolean = true,
)

@Serializable
internal data class AuPlaylist(
    val id: String,
    @SerialName("playlist_name") val name: String = "",
    val user: AuUser? = null,
    val artwork: AuArtwork? = null,
    @SerialName("track_count") val trackCount: Int? = null,
)
