package space.wavedot.core.domain.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import space.wavedot.core.common.TimeProvider
import space.wavedot.core.common.toWavedotError
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.contract.SourceKind
import space.wavedot.core.domain.library.ChangeOrigin
import space.wavedot.core.domain.library.FavoritesRepository
import space.wavedot.core.model.Cursor
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track

class SyncLibrary(
    private val registry: MediaSourceRegistry,
    private val favorites: FavoritesRepository,
    private val gateway: SyncGateway,
    private val stateStore: SyncStateStore,
    private val time: TimeProvider,
    private val importLimit: Int = DEFAULT_IMPORT_LIMIT,
    private val pushBatchSize: Int = DEFAULT_PUSH_BATCH_SIZE,
) {
    suspend operator fun invoke(): SyncReport {
        val errors = mutableListOf<String>()
        val imported = importRemoteLikes(errors)
        var pushed = 0
        var pulled = 0
        if (gateway.isConfigured()) {
            guard(errors, "hub push") { pushed = pushPending() }
            guard(errors, "hub pull") { pulled = pullRemote() }
        }
        return SyncReport(imported, pushed, pulled, errors)
    }

    private suspend fun importRemoteLikes(errors: MutableList<String>): Int {
        var count = 0
        registry.sources.value.filter { it.library != null }.forEach { source ->
            guard(errors, source.descriptor.displayName) {
                val known = favorites.observeIds().first()
                val now = time.nowMillis()
                likedTracks(source).filter { it.id !in known }.forEach { track ->
                    favorites.add(track, now, ChangeOrigin.LOCAL)
                    count++
                }
            }
        }
        return count
    }

    private suspend fun likedTracks(source: MediaSource): List<Track> {
        val query = source.library!!.likedTracks
        val result = mutableListOf<Track>()
        var cursor: Cursor? = null
        do {
            val page = query.page(Unit, cursor)
            result += page.items
            cursor = page.next
        } while (cursor != null && result.size < importLimit)
        return result.take(importLimit)
    }

    private suspend fun pushPending(): Int {
        val (deviceOnly, portable) = favorites.pendingChanges().partition { isDeviceOnly(it.id.source) }
        if (deviceOnly.isNotEmpty()) favorites.markSynced(deviceOnly)
        portable.chunked(pushBatchSize).forEach { batch ->
            gateway.push(batch.map { SyncRecord.Favorite(it) })
            favorites.markSynced(batch)
        }
        return portable.size
    }

    private suspend fun pullRemote(): Int {
        var revision = stateStore.lastRevision()
        var total = 0
        do {
            val batch = gateway.pull(revision)
            val changes = batch.records
                .filterIsInstance<SyncRecord.Favorite>()
                .map { it.change }
                .filterNot { isDeviceOnly(it.id.source) }
            if (changes.isNotEmpty()) favorites.applyRemote(changes)
            total += changes.size
            revision = batch.revision
            stateStore.saveRevision(revision)
        } while (batch.hasMore)
        return total
    }

    private fun isDeviceOnly(source: SourceId): Boolean =
        registry.find(source)?.descriptor?.kind == SourceKind.LOCAL

    private suspend fun guard(errors: MutableList<String>, scope: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            errors += "$scope: ${t.toWavedotError().message}"
        }
    }

    companion object {
        const val DEFAULT_IMPORT_LIMIT: Int = 5000
        const val DEFAULT_PUSH_BATCH_SIZE: Int = 500
    }
}
