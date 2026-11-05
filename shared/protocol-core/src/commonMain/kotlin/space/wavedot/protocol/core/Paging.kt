package space.wavedot.protocol.core

import kotlinx.serialization.Serializable

@Serializable
data class PageDto<T>(
    val items: List<T>,
    val nextCursor: String? = null,
)
