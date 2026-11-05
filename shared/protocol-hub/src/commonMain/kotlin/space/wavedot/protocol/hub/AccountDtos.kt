package space.wavedot.protocol.hub

import kotlinx.serialization.Serializable
import space.wavedot.protocol.core.PlatformDto

@Serializable
data class CreateAccountRequest(
    val deviceName: String,
    val platform: PlatformDto,
)

@Serializable
data class CreateAccountResponse(
    val accountId: String,
    val deviceId: String,
    val deviceToken: String,
)
