package space.wavedot.core.domain.library

import kotlinx.coroutines.test.runTest
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.InMemoryFavoritesRepository
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToggleFavoriteTest {
    private val track = TestTracks.track("1")
    private val source = FakeMediaSource(tracks = listOf(track), withLibrary = true)
    private val favorites = InMemoryFavoritesRepository()
    private val toggle = ToggleFavorite(favorites, TestRegistry(source), { 1_000L })

    @Test
    fun togglesAndMirrorsToSourceLibrary() = runTest {
        assertTrue(toggle(track))
        assertTrue(favorites.isFavorite(track.id))
        assertEquals(listOf(track), source.liked)

        assertFalse(toggle(track))
        assertFalse(favorites.isFavorite(track.id))
        assertTrue(source.liked.isEmpty())
    }

    @Test
    fun recordsPendingChangeForSync() = runTest {
        toggle(track)
        val change = favorites.pendingChanges().single()
        assertEquals(track.id, change.id)
        assertFalse(change.deleted)
    }
}
