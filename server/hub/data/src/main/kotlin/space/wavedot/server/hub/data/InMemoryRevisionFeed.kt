package space.wavedot.server.hub.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import space.wavedot.server.hub.domain.account.AccountId
import space.wavedot.server.hub.domain.sync.ChangeNotifier
import space.wavedot.server.hub.domain.sync.RevisionFeed

class InMemoryRevisionFeed : ChangeNotifier, RevisionFeed {
    private class Topic {
        val revisions = MutableSharedFlow<Long>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        var subscribers = 0
    }

    private val lock = Any()
    private val topics = HashMap<AccountId, Topic>()

    val activeAccounts: Int get() = synchronized(lock) { topics.size }

    override fun revisionChanged(accountId: AccountId, revision: Long) {
        val topic = synchronized(lock) { topics[accountId] } ?: return
        topic.revisions.tryEmit(revision)
    }

    override fun subscribe(accountId: AccountId): Flow<Long> = flow {
        val topic = synchronized(lock) { topics.getOrPut(accountId, ::Topic).also { it.subscribers++ } }
        try {
            emitAll(topic.revisions)
        } finally {
            synchronized(lock) { if (--topic.subscribers == 0) topics.remove(accountId) }
        }
    }
}
