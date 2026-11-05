package space.wavedot.core.domain.player

import space.wavedot.core.testing.TestTracks
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackQueueTest {
    private val tracks = TestTracks.tracks(5)

    @Test
    fun advancesLinearlyAndEndsWithoutRepeat() {
        var queue: PlaybackQueue? = PlaybackQueue.of(tracks, startIndex = 3)
        queue = queue!!.next(auto = true)
        assertEquals(tracks[4], queue!!.current)
        assertNull(queue.next(auto = true))
    }

    @Test
    fun repeatAllWrapsAround() {
        val queue = PlaybackQueue.of(tracks, startIndex = 4, repeat = RepeatMode.ALL)
        assertEquals(tracks[0], queue.next(auto = true)!!.current)
        assertEquals(tracks[4], PlaybackQueue.of(tracks, repeat = RepeatMode.ALL).previous().current)
    }

    @Test
    fun repeatOneStaysOnTrackOnlyForAutoAdvance() {
        val queue = PlaybackQueue.of(tracks, startIndex = 1, repeat = RepeatMode.ONE)
        assertEquals(tracks[1], queue.next(auto = true)!!.current)
        assertEquals(tracks[2], queue.next(auto = false)!!.current)
    }

    @Test
    fun shuffleKeepsCurrentTrackFirstAndRestoresOrder() {
        val queue = PlaybackQueue.of(tracks, startIndex = 2)
        val shuffled = queue.withShuffle(true, Random(42))
        assertEquals(tracks[2], shuffled.current)
        assertEquals(0, shuffled.position)
        assertEquals(tracks.toSet(), shuffled.orderedTracks.toSet())
        val restored = shuffled.withShuffle(false)
        assertEquals(tracks, restored.orderedTracks)
        assertEquals(tracks[2], restored.current)
    }

    @Test
    fun playNextInsertsAfterCurrent() {
        val extra = TestTracks.track("extra")
        val queue = PlaybackQueue.of(tracks, startIndex = 1).playNext(extra)
        assertEquals(extra, queue.next(auto = false)!!.current)
    }

    @Test
    fun removeBeforeCurrentKeepsCurrentTrack() {
        val queue = PlaybackQueue.of(tracks, startIndex = 3).remove(0)
        assertEquals(tracks[3], queue.current)
        assertEquals(4, queue.order.size)
    }

    @Test
    fun appendToEmptyQueueSelectsFirstTrack() {
        val queue = PlaybackQueue().append(tracks.take(2))
        assertEquals(tracks[0], queue.current)
        assertTrue(!queue.isEmpty)
    }
}
