package space.wavedot.server.hub.domain.sync

import space.wavedot.server.hub.domain.account.AccountRepository
import space.wavedot.server.hub.domain.account.DeviceSession
import space.wavedot.server.kernel.TransactionRunner
import space.wavedot.server.kernel.validate

data class PushResult(
    val revision: Long,
    val rejected: List<RejectedChange>,
)

class PushChanges(
    private val accounts: AccountRepository,
    private val library: LibraryRepository,
    private val collections: CollectionRegistry,
    private val transactions: TransactionRunner,
    private val notifier: ChangeNotifier,
    private val maxBatch: Int = 500,
) {
    suspend operator fun invoke(session: DeviceSession, changes: List<Change>): PushResult {
        validate(changes.size <= maxBatch) { "At most $maxBatch changes per request" }
        val rejected = mutableListOf<RejectedChange>()
        var lastRevision = 0L
        var applied = 0
        transactions.inTransaction {
            for (change in changes) {
                val policy = collections.policyFor(change.collection)
                val reason = if (policy == null) "Unknown collection ${change.collection}" else policy.reject(change)
                if (reason != null) {
                    rejected += RejectedChange(change, reason)
                    continue
                }
                val existing = library.find(session.accountId, change.collection, change.key)
                if (existing != null && existing.change.updatedAt > change.updatedAt) {
                    rejected += RejectedChange(change, "A newer change already exists")
                    continue
                }
                lastRevision = accounts.nextRevision(session.accountId)
                library.upsert(session.accountId, StoredChange(lastRevision, session.deviceId, change))
                applied++
            }
        }
        if (applied > 0) notifier.revisionChanged(session.accountId, lastRevision)
        return PushResult(lastRevision, rejected)
    }
}

data class PullResult(
    val changes: List<StoredChange>,
    val revision: Long,
    val hasMore: Boolean,
)

class PullChanges(
    private val library: LibraryRepository,
    private val maxLimit: Int = 500,
) {
    suspend operator fun invoke(session: DeviceSession, sinceRevision: Long, limit: Int = maxLimit): PullResult {
        validate(sinceRevision >= 0) { "Revision must not be negative" }
        val effective = limit.coerceIn(1, maxLimit)
        val page = library.changesSince(session.accountId, sinceRevision, effective + 1)
        val items = page.take(effective)
        return PullResult(items, items.lastOrNull()?.revision ?: sinceRevision, page.size > effective)
    }
}
