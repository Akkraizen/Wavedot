package space.wavedot.ui.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FormattingTest {
    @Test
    fun formatsLengths() {
        assertEquals("0 min", 30.seconds.toLength())
        assertEquals("42 min", 42.minutes.toLength())
        assertEquals("2 h", 120.minutes.toLength())
        assertEquals("1 h 5 min", 65.minutes.toLength())
    }

    @Test
    fun formatsTrackCounts() {
        assertEquals("1 track", trackCount(1))
        assertEquals("3 tracks", trackCount(3))
    }
}
