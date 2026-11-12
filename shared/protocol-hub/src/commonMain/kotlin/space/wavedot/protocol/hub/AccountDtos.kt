package space.wavedot.protocol.hub

import kotlinx.serialization.Serializable
import space.wavedot.protocol.core.PlatformDto

@Serializable
data class CreateAccountRequest(
    val deviceName: String,
    val platform: PlatformDto,
    val registrationToken: String? = null,
)

@Serializable
data class CreateAccountResponse(
    val accountId: String,
    val deviceId: String,
    val deviceToken: String,
)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String,
    val platform: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val current: Boolean = false,
)

@Serializable
data class DevicesResponse(
    val devices: List<DeviceDto>,
)
