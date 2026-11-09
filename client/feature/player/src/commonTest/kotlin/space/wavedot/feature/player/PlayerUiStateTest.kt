package space.wavedot.feature.player

import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PlayerUiStateTest {
    @Test
    fun tracksWithoutDurationAreLive() {
        val station = TestTracks.track("radio").copy(duration = null)
        assertTrue(PlayerUiState(track = station).isLive)
        assertFalse(PlayerUiState(track = station, duration = 3.minutes).isLive)
        assertFalse(PlayerUiState().isLive)
    }
}
