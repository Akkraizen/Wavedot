package space.wavedot.protocol.core

import kotlinx.serialization.Serializable

@Serializable
data class ProblemDetails(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
)

object ProblemTypes {
    const val NOT_FOUND: String = "urn:wavedot:problem:not-found"
    const val UNAUTHORIZED: String = "urn:wavedot:problem:unauthorized"
    const val FORBIDDEN: String = "urn:wavedot:problem:forbidden"
    const val VALIDATION: String = "urn:wavedot:problem:validation"
    const val CONFLICT: String = "urn:wavedot:problem:conflict"
    const val INTERNAL: String = "urn:wavedot:problem:internal"
    const val RATE_LIMITED: String = "urn:wavedot:problem:rate-limited"
    const val PAYLOAD_TOO_LARGE: String = "urn:wavedot:problem:payload-too-large"
    const val UNSUPPORTED_PROTOCOL: String = "urn:wavedot:problem:unsupported-protocol"
}
