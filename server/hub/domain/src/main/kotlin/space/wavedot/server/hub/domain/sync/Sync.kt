package space.wavedot.server.hub.domain.sync

import kotlinx.coroutines.flow.Flow
import space.wavedot.server.hub.domain.account.AccountId
import space.wavedot.server.hub.domain.account.DeviceId
import java.time.Duration

data class Change(
    val collection: String,
    val key: String,
    val deleted: Boolean,
    val payload: String?,
    val updatedAt: Long,
)

data class StoredChange(
    val revision: Long,
    val deviceId: DeviceId,
    val change: Change,
)

data class RejectedChange(
    val change: Change,
    val reason: String,
)

data class SyncLimits(
    val maxBatch: Int = 500,
    val maxPayloadBytes: Int = 16 * 1024,
    val maxItemsPerAccount: Long = 50_000,
    val maxClockSkew: Duration = Duration.ofHours(24),
)

interface LibraryRepository {
    suspend fun find(accountId: AccountId, collection: String, key: String): StoredChange?

    suspend fun upsert(accountId: AccountId, stored: StoredChange)

    suspend fun changesSince(accountId: AccountId, revision: Long, limit: Int): List<StoredChange>

    suspend fun count(accountId: AccountId): Long
}

fun interface ChangeNotifier {
    fun revisionChanged(accountId: AccountId, revision: Long)
}

fun interface RevisionFeed {
    fun subscribe(accountId: AccountId): Flow<Long>
}

interface CollectionPolicy {
    val name: String

    fun reject(change: Change): String?
}

open class DefaultCollectionPolicy(
    override val name: String,
    private val maxPayloadBytes: Int = SyncLimits().maxPayloadBytes,
) : CollectionPolicy {
    override fun reject(change: Change): String? = when {
        change.key.isBlank() || change.key.length > MAX_KEY_LENGTH -> "Key must be 1..$MAX_KEY_LENGTH characters"
        !change.deleted && change.payload == null -> "Payload is required for upserts"
        change.payload != null && !change.payload.trimStart().startsWith('{') -> "Payload must be a JSON object"
        (change.payload?.encodeToByteArray()?.size ?: 0) > maxPayloadBytes -> "Payload exceeds $maxPayloadBytes bytes"
        else -> null
    }

    private companion object {
        const val MAX_KEY_LENGTH = 512
    }
}

class MediaCollectionPolicy(
    name: String,
    maxPayloadBytes: Int = SyncLimits().maxPayloadBytes,
) : DefaultCollectionPolicy(name, maxPayloadBytes) {
    override fun reject(change: Change): String? =
        super.reject(change) ?: if (MEDIA_KEY.matches(change.key)) null else "Key must look like <source>:<id>"

    private companion object {
        val MEDIA_KEY = Regex("""^[^:\s]+:.+$""", RegexOption.DOT_MATCHES_ALL)
    }
}

class CollectionRegistry(policies: List<CollectionPolicy>) {
    private val byName = policies.associateBy { it.name }

    fun policyFor(collection: String): CollectionPolicy? = byName[collection]

    companion object {
        const val FAVORITES: String = "favorites"
        val DEFAULT_COLLECTIONS: List<String> = listOf(FAVORITES, "playlists", "history", "settings")

        fun default(limits: SyncLimits = SyncLimits()): CollectionRegistry = CollectionRegistry(
            DEFAULT_COLLECTIONS.map { name ->
                if (name == FAVORITES) MediaCollectionPolicy(name, limits.maxPayloadBytes)
                else DefaultCollectionPolicy(name, limits.maxPayloadBytes)
            },
        )
    }
}
