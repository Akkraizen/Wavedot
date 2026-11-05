package space.wavedot.protocol.updates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto

@Serializable
enum class ChannelDto {
    @SerialName("stable") STABLE,
    @SerialName("beta") BETA,
    @SerialName("nightly") NIGHTLY,
}

@Serializable
data class ArtifactDto(
    val platform: PlatformDto,
    val arch: ArchDto,
    val fileName: String,
    val size: Long,
    val sha256: String,
    val url: String,
)

@Serializable
data class ReleaseDto(
    val id: String,
    val version: String,
    val channel: ChannelDto,
    val publishedAt: Long,
    val notes: String,
    val mandatory: Boolean,
    val artifact: ArtifactDto,
)

@Serializable
data class PublishReleaseRequest(
    val version: String,
    val channel: ChannelDto,
    val notes: String = "",
    val mandatory: Boolean = false,
    val rolloutPercent: Int = 100,
)

@Serializable
data class PublishedReleaseDto(
    val id: String,
    val version: String,
    val channel: ChannelDto,
    val artifacts: List<String>,
)
