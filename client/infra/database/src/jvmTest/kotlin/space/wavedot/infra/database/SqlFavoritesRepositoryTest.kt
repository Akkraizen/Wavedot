package space.wavedot.infra.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import space.wavedot.core.domain.library.ChangeOrigin
import space.wavedot.core.domain.library.Favorite
import space.wavedot.core.domain.library.FavoriteChange
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlFavoritesRepositoryTest {
    private val database = createJvmDatabase(null)
    private val repository = SqlFavoritesRepository(database, Dispatchers.Unconfined)

    @Test
    fun storesFavoritesAndTracksPendingChanges() = runTest {
        val track = TestTracks.track("1")
        repository.add(track, at = 10L)
        assertTrue(repository.isFavorite(track.id))

        val stored = repository.observeAll().first().single()
        assertEquals(track.title, stored.track.title)
        assertEquals(track.artistLine, stored.track.artistLine)
        assertEquals(track.duration, stored.track.duration)

        val pending = repository.pendingChanges().single()
        assertFalse(pending.deleted)
        repository.markSynced(listOf(pending))
        assertTrue(repository.pendingChanges().isEmpty())
    }

    @Test
    fun removalIsRecordedAsDeletion() = runTest {
        val track = TestTracks.track("2")
        repository.add(track, at = 1L)
        repository.remove(track.id, at = 2L)
        val pending = repository.pendingChanges().single()
        assertTrue(pending.deleted)
        assertEquals(2L, pending.updatedAt)
    }

    @Test
    fun remoteChangesDoNotCreatePendingEntries() = runTest {
        val track = TestTracks.track("3")
        repository.applyRemote(listOf(FavoriteChange(track.id, Favorite(track, 5L), 5L)))
        assertEquals(setOf(track.id), repository.observeIds().first())
        assertTrue(repository.pendingChanges().isEmpty())
        repository.remove(track.id, 6L, ChangeOrigin.REMOTE)
        assertTrue(repository.observeIds().first().isEmpty())
    }

    @Test
    fun syncRevisionRoundTrips() = runTest {
        val store = SqlSyncStateStore(database, Dispatchers.Unconfined)
        assertEquals(0L, store.lastRevision())
        store.saveRevision(42L)
        assertEquals(42L, store.lastRevision())
    }
}
