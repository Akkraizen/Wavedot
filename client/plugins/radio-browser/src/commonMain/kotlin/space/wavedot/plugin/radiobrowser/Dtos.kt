package space.wavedot.plugin.radiobrowser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RbStation(
    @SerialName("stationuuid") val stationUuid: String,
    val name: String = "",
    val url: String = "",
    @SerialName("url_resolved") val urlResolved: String = "",
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Int = 0,
)

@Serializable
internal data class RbStreamUrl(
    val ok: Boolean = false,
    val url: String = "",
)
