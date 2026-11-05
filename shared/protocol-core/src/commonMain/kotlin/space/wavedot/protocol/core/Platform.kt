package space.wavedot.protocol.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlatformDto {
    @SerialName("android") ANDROID,
    @SerialName("windows") WINDOWS,
    @SerialName("linux") LINUX,
    @SerialName("macos") MACOS,
}

@Serializable
enum class ArchDto {
    @SerialName("x64") X64,
    @SerialName("arm64") ARM64,
    @SerialName("universal") UNIVERSAL,
}
