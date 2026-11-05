package space.wavedot.core.domain.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import space.wavedot.core.domain.library.Favorite
import space.wavedot.core.domain.library.FavoriteChange
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.InMemoryFavoritesRepository
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncLibraryTest {
    private class FakeGateway(var remote: List<SyncRecord> = emptyList()) : SyncGateway {
        val pushed = mutableListOf<SyncRecord>()
        var configured = true

        override suspend fun isConfigured(): Boolean = configured

        override suspend fun push(records: List<SyncRecord>): Long {
            pushed += records
            return pushed.size.toLong()
        }

        override suspend fun pull(sinceRevision: Long): PullBatch {
            val batch = remote.drop(sinceRevision.toInt()).take(1)
            val revision = sinceRevision + batch.size
            return PullBatch(batch, revision, hasMore = revision < remote.size)
        }
    }

    private class MemoryState : SyncStateStore {
        var revision = 0L
        override suspend fun lastRevision(): Long = revision
        override suspend fun saveRevision(revision: Long) {
            this.revision = revision
        }
    }

    @Test
    fun importsRemoteLikesPushesAndPulls() = runTest {
        val liked = TestTracks.tracks(3)
        val source = FakeMediaSource(tracks = liked, liked = liked, withLibrary = true)
        val favorites = InMemoryFavoritesRepository()
        val incoming = TestTracks.track("remote")
        val gateway = FakeGateway(
            remote = listOf(
                SyncRecord.Favorite(FavoriteChange(incoming.id, Favorite(incoming, 5L), 5L)),
                SyncRecord.Favorite(FavoriteChange(liked[0].id, null, 6L)),
            ),
        )
        val state = MemoryState()
        val sync = SyncLibrary(TestRegistry(source), favorites, gateway, state, { 1L })

        val report = sync()

        assertEquals(3, report.imported)
        assertEquals(3, report.pushed)
        assertEquals(2, report.pulled)
        assertTrue(report.errors.isEmpty())
        assertEquals(2L, state.revision)
        assertTrue(favorites.pendingChanges().isEmpty())
        val ids = favorites.observeIds().first()
        assertEquals(setOf(liked[1].id, liked[2].id, incoming.id), ids)
    }

    @Test
    fun skipsHubWhenNotConfigured() = runTest {
        val gateway = FakeGateway().apply { configured = false }
        val favorites = InMemoryFavoritesRepository()
        favorites.add(TestTracks.track("1"), 1L)
        val report = SyncLibrary(TestRegistry(), favorites, gateway, MemoryState(), { 1L })()
        assertEquals(0, report.pushed)
        assertEquals(1, favorites.pendingChanges().size)
    }
}
