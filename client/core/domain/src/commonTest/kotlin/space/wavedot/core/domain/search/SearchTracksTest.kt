package space.wavedot.core.domain.search

import kotlinx.coroutines.test.runTest
import space.wavedot.core.model.SourceId
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTracksTest {
    private val good = FakeMediaSource(SourceId("good"), tracks = TestTracks.tracks(30, SourceId("good")))
    private val broken = FakeMediaSource(SourceId("broken"), searchFailure = IllegalStateException("down"))
    private val search = SearchTracks(TestRegistry(good, broken))

    @Test
    fun aggregatesResultsPerSourceAndIsolatesFailures() = runTest {
        val results = search("track", limit = 10)
        assertEquals(2, results.size)
        val ok = results.first { it.source.id == SourceId("good") }
        assertEquals(10, ok.page.items.size)
        assertNotNull(ok.page.next)
        assertNull(ok.error)
        assertNotNull(results.first { it.source.id == SourceId("broken") }.error)
    }

    @Test
    fun blankQueryReturnsNothing() = runTest {
        assertTrue(search("   ").isEmpty())
    }

    @Test
    fun canTargetSingleSource() = runTest {
        assertEquals(listOf(SourceId("good")), search("track", only = SourceId("good")).map { it.source.id })
    }
}
