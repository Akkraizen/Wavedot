package space.wavedot.infra.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import space.wavedot.core.model.Artwork
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SqlPlaylistRepositoryTest {
    private val repository = SqlPlaylistRepository(createJvmDatabase(null), Dispatchers.Unconfined)
    private val tracks = TestTracks.tracks(5).mapIndexed { index, track -> track.copy(artwork = Artwork("cover-$index")) }

    @Test
    fun keepsTracksInInsertionOrderWithoutDuplicates() = runTest {
        repository.create("p", "Mix", 1)
        assertEquals(2, repository.add("p", tracks.take(2), 2))
        assertEquals(1, repository.add("p", listOf(tracks[1], tracks[2]), 3))

        val details = repository.observe("p").first()!!
        assertEquals(tracks.take(3), details.tracks)
        assertEquals(3, details.summary.trackCount)
        assertEquals(540.seconds, details.summary.duration)
        assertEquals(3, details.summary.updatedAt)
    }

    @Test
    fun summariesCarryCoversAndAreOrderedByLastChange() = runTest {
        repository.create("a", "First", 1)
        repository.create("b", "Second", 2)
        repository.add("a", tracks, 5)

        val summaries = repository.observeAll().first()
        assertEquals(listOf("a", "b"), summaries.map { it.id })
        assertEquals(listOf("cover-0", "cover-1", "cover-2", "cover-3"), summaries.first().covers)
        assertEquals(0, summaries.last().trackCount)
    }

    @Test
    fun appendsAfterRemovedTracks() = runTest {
        repository.create("p", "Mix", 1)
        repository.add("p", tracks.take(3), 2)
        repository.remove("p", listOf(tracks[0].id, tracks[2].id), 3)
        repository.add("p", listOf(tracks[3], tracks[0]), 4)

        assertEquals(listOf(tracks[1], tracks[3], tracks[0]), repository.observe("p").first()!!.tracks)
    }

    @Test
    fun reportsMembershipAcrossPlaylists() = runTest {
        repository.create("a", "A", 1)
        repository.create("b", "B", 1)
        repository.add("a", tracks.take(2), 2)
        repository.add("b", tracks.drop(1).take(1), 2)

        val ids = tracks.take(2).map { it.id }
        assertEquals(mapOf("a" to 2, "b" to 1), repository.observeContaining(ids).first())
        assertEquals(setOf(tracks[1].id), repository.contained("b", ids))
    }

    @Test
    fun renamesAndDeletes() = runTest {
        repository.create("p", "Mix", 1)
        repository.add("p", tracks.take(1), 2)
        repository.rename("p", "Evening", 3)
        assertEquals("Evening", repository.observe("p").first()!!.summary.title)

        repository.delete("p")
        assertNull(repository.observe("p").first())
        assertTrue(repository.observeContaining(listOf(tracks[0].id)).first().isEmpty())
    }
}
