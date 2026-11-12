package space.wavedot.server.hub.domain.sync

import space.wavedot.server.hub.domain.account.AccountRepository
import space.wavedot.server.hub.domain.account.DeviceSession
import space.wavedot.server.kernel.TransactionRunner
import space.wavedot.server.kernel.validate
import java.time.Clock

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
    private val clock: Clock,
    private val limits: SyncLimits = SyncLimits(),
) {
    private data class Outcome(val before: Long, val after: Long, val rejected: List<RejectedChange>)

    suspend operator fun invoke(session: DeviceSession, changes: List<Change>): PushResult {
        validate(changes.size <= limits.maxBatch) { "At most ${limits.maxBatch} changes per request" }
        val latestAllowed = clock.millis() + limits.maxClockSkew.toMillis()
        val outcome = transactions.inTransaction { apply(session, changes, latestAllowed) }
        if (outcome.after != outcome.before) notifier.revisionChanged(session.accountId, outcome.after)
        return PushResult(outcome.after, outcome.rejected)
    }

    private suspend fun apply(session: DeviceSession, changes: List<Change>, latestAllowed: Long): Outcome {
        val before = accounts.lockRevision(session.accountId)
        val rejected = mutableListOf<RejectedChange>()
        var revision = before
        var items: Long? = null
        for (change in changes) {
            val reason = rejectionReason(change, latestAllowed)
            if (reason != null) {
                rejected += RejectedChange(change, reason)
                continue
            }
            val existing = library.find(session.accountId, change.collection, change.key)
            if (existing != null && existing.change.updatedAt > change.updatedAt) {
                rejected += RejectedChange(change, "A newer change already exists")
                continue
            }
            if (existing == null) {
                val count = items ?: library.count(session.accountId)
                if (count >= limits.maxItemsPerAccount) {
                    items = count
                    rejected += RejectedChange(change, "The account holds the maximum of ${limits.maxItemsPerAccount} items")
                    continue
                }
                items = count + 1
            }
            revision++
            library.upsert(session.accountId, StoredChange(revision, session.deviceId, change))
        }
        if (revision != before) accounts.saveRevision(session.accountId, revision)
        return Outcome(before, revision, rejected)
    }

    private fun rejectionReason(change: Change, latestAllowed: Long): String? {
        val policy = collections.policyFor(change.collection) ?: return "Unknown collection ${change.collection}"
        return policy.reject(change) ?: when {
            change.updatedAt < 0 -> "Timestamp must not be negative"
            change.updatedAt > latestAllowed -> "Timestamp is too far in the future"
            else -> null
        }
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
