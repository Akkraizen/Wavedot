package space.wavedot.server.updates.api

import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.updates.ChannelDto
import space.wavedot.server.updates.domain.Arch
import space.wavedot.server.updates.domain.Channel
import space.wavedot.server.updates.domain.Platform

internal fun PlatformDto.toDomain(): Platform = when (this) {
    PlatformDto.ANDROID -> Platform.ANDROID
    PlatformDto.WINDOWS -> Platform.WINDOWS
    PlatformDto.LINUX -> Platform.LINUX
    PlatformDto.MACOS -> Platform.MACOS
}

internal fun Platform.toDto(): PlatformDto = when (this) {
    Platform.ANDROID -> PlatformDto.ANDROID
    Platform.WINDOWS -> PlatformDto.WINDOWS
    Platform.LINUX -> PlatformDto.LINUX
    Platform.MACOS -> PlatformDto.MACOS
}

internal fun ArchDto.toDomain(): Arch = when (this) {
    ArchDto.X64 -> Arch.X64
    ArchDto.ARM64 -> Arch.ARM64
    ArchDto.UNIVERSAL -> Arch.UNIVERSAL
}

internal fun Arch.toDto(): ArchDto = when (this) {
    Arch.X64 -> ArchDto.X64
    Arch.ARM64 -> ArchDto.ARM64
    Arch.UNIVERSAL -> ArchDto.UNIVERSAL
}

internal fun ChannelDto.toDomain(): Channel = when (this) {
    ChannelDto.STABLE -> Channel.STABLE
    ChannelDto.BETA -> Channel.BETA
    ChannelDto.NIGHTLY -> Channel.NIGHTLY
}

internal fun Channel.toDto(): ChannelDto = when (this) {
    Channel.STABLE -> ChannelDto.STABLE
    Channel.BETA -> ChannelDto.BETA
    Channel.NIGHTLY -> ChannelDto.NIGHTLY
}
