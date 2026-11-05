package space.wavedot.infra.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import space.wavedot.core.domain.sync.SyncStateStore

class SqlSyncStateStore(
    private val database: WavedotDatabase,
    private val dispatcher: CoroutineDispatcher,
) : SyncStateStore {
    override suspend fun lastRevision(): Long = withContext(dispatcher) {
        database.syncStateQueries.selectRevision().executeAsOneOrNull() ?: 0L
    }

    override suspend fun saveRevision(revision: Long) {
        withContext(dispatcher) { database.syncStateQueries.saveRevision(revision) }
    }
}
