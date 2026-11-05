package space.wavedot.server.hub.domain.sync

import space.wavedot.server.hub.domain.account.AccountId
import space.wavedot.server.hub.domain.account.DeviceId

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

interface LibraryRepository {
    suspend fun find(accountId: AccountId, collection: String, key: String): StoredChange?

    suspend fun upsert(accountId: AccountId, stored: StoredChange)

    suspend fun changesSince(accountId: AccountId, revision: Long, limit: Int): List<StoredChange>
}

fun interface ChangeNotifier {
    fun revisionChanged(accountId: AccountId, revision: Long)
}

interface CollectionPolicy {
    val name: String

    fun reject(change: Change): String?
}

class DefaultCollectionPolicy(
    override val name: String,
    private val maxPayloadBytes: Int = 16 * 1024,
) : CollectionPolicy {
    override fun reject(change: Change): String? = when {
        change.key.isBlank() || change.key.length > 512 -> "Key must be 1..512 characters"
        !change.deleted && change.payload == null -> "Payload is required for upserts"
        (change.payload?.length ?: 0) > maxPayloadBytes -> "Payload exceeds $maxPayloadBytes bytes"
        else -> null
    }
}

class CollectionRegistry(policies: List<CollectionPolicy>) {
    private val byName = policies.associateBy { it.name }

    fun policyFor(collection: String): CollectionPolicy? = byName[collection]

    companion object {
        val DEFAULT_COLLECTIONS: List<String> = listOf("favorites", "playlists", "history", "settings")

        fun default(): CollectionRegistry = CollectionRegistry(DEFAULT_COLLECTIONS.map { DefaultCollectionPolicy(it) })
    }
}
