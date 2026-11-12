package space.wavedot.core.domain.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import space.wavedot.core.domain.library.Favorite
import space.wavedot.core.contract.SourceKind
import space.wavedot.core.domain.library.FavoriteChange
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
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
        val batches = mutableListOf<Int>()
        var configured = true

        override suspend fun isConfigured(): Boolean = configured

        override suspend fun push(records: List<SyncRecord>): Long {
            pushed += records
            batches += records.size
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

    @Test
    fun pushesPendingChangesInBatches() = runTest {
        val favorites = InMemoryFavoritesRepository()
        TestTracks.tracks(5).forEach { favorites.add(it, 1L) }
        val gateway = FakeGateway()

        val report = SyncLibrary(TestRegistry(), favorites, gateway, MemoryState(), { 1L }, pushBatchSize = 2)()

        assertEquals(listOf(2, 2, 1), gateway.batches)
        assertEquals(5, report.pushed)
        assertTrue(favorites.pendingChanges().isEmpty())
    }

    @Test
    fun keepsDeviceOnlyFavoritesOffTheHub() = runTest {
        val localSource = SourceId("local")
        val localTrack = Track(MediaId(localSource, "/music/a.mp3"), "A")
        val foreign = Track(MediaId(localSource, "/other/device.mp3"), "B")
        val favorites = InMemoryFavoritesRepository()
        favorites.add(localTrack, 1L)
        val gateway = FakeGateway(remote = listOf(SyncRecord.Favorite(FavoriteChange(foreign.id, Favorite(foreign, 2L), 2L))))
        val registry = TestRegistry(FakeMediaSource(id = localSource, kind = SourceKind.LOCAL))

        val report = SyncLibrary(registry, favorites, gateway, MemoryState(), { 1L })()

        assertEquals(0, report.pushed)
        assertEquals(0, report.pulled)
        assertTrue(gateway.pushed.isEmpty())
        assertTrue(favorites.pendingChanges().isEmpty())
        assertEquals(setOf(localTrack.id), favorites.observeIds().first())
    }
}
