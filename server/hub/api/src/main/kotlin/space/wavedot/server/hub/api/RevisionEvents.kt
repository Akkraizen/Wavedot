package space.wavedot.server.hub.api

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import space.wavedot.server.hub.domain.account.AccountId
import space.wavedot.server.hub.domain.sync.ChangeNotifier

class RevisionEvents : ChangeNotifier {
    private val events = MutableSharedFlow<Pair<AccountId, Long>>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun revisionChanged(accountId: AccountId, revision: Long) {
        events.tryEmit(accountId to revision)
    }

    fun subscribe(accountId: AccountId): Flow<Long> = events.filter { it.first == accountId }.map { it.second }
}
