package space.wavedot.protocol.hub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChangeDto(
    val collection: String,
    val key: String,
    val deleted: Boolean,
    val payload: JsonElement? = null,
    val updatedAt: Long,
)

@Serializable
data class PushChangesRequest(
    val changes: List<ChangeDto>,
)

@Serializable
data class RejectedChangeDto(
    val collection: String,
    val key: String,
    val reason: String,
)

@Serializable
data class PushChangesResponse(
    val revision: Long,
    val rejected: List<RejectedChangeDto> = emptyList(),
)

@Serializable
data class RevisionedChangeDto(
    val revision: Long,
    val deviceId: String,
    val change: ChangeDto,
)

@Serializable
data class PullChangesResponse(
    val changes: List<RevisionedChangeDto>,
    val revision: Long,
    val hasMore: Boolean,
)

@Serializable
data class RevisionEvent(
    val revision: Long,
)
