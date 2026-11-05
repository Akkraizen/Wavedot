package space.wavedot.core.domain.sync

import space.wavedot.core.domain.library.FavoriteChange

sealed interface SyncRecord {
    val updatedAt: Long

    data class Favorite(val change: FavoriteChange) : SyncRecord {
        override val updatedAt: Long get() = change.updatedAt
    }
}

data class PullBatch(
    val records: List<SyncRecord>,
    val revision: Long,
    val hasMore: Boolean,
)

interface SyncGateway {
    suspend fun isConfigured(): Boolean

    suspend fun push(records: List<SyncRecord>): Long

    suspend fun pull(sinceRevision: Long): PullBatch
}

interface SyncStateStore {
    suspend fun lastRevision(): Long

    suspend fun saveRevision(revision: Long)
}

data class SyncReport(
    val imported: Int = 0,
    val pushed: Int = 0,
    val pulled: Int = 0,
    val errors: List<String> = emptyList(),
)
