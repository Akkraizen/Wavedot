package space.wavedot.core.domain.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.core.testing.FakeAudioEngine
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import space.wavedot.playback.api.EngineStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerTest {
    private val tracks = TestTracks.tracks(3)
    private val engine = FakeAudioEngine()

    private fun TestScope.controller(): PlayerController {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler)).backgroundScope
        val registry = TestRegistry(FakeMediaSource(tracks = tracks))
        return PlayerController(engine, ResolveStream(registry), FixedPlaybackPreferences(AudioQuality.HIGH), scope)
    }

    @Test
    fun playResolvesStreamAndStartsEngine() = runTest {
        val player = controller()
        player.play(tracks, startIndex = 1)
        val loaded = engine.loaded.single() as StreamSource.Remote
        assertTrue(loaded.url.endsWith("/2?q=HIGH"))
        assertEquals(tracks[1], player.state.value.current)
        assertEquals(EngineStatus.PLAYING, player.state.value.engine.status)
    }

    @Test
    fun advancesWhenEngineFinishesTrack() = runTest {
        val player = controller()
        player.play(tracks)
        engine.finishCurrent()
        assertEquals(tracks[1], player.state.value.current)
        assertEquals(2, engine.loaded.size)
    }

    @Test
    fun previousRestartsTrackAfterThreshold() = runTest {
        val player = controller()
        player.play(tracks, startIndex = 1)
        engine.seekTo(10.seconds)
        player.previous()
        assertEquals(tracks[1], player.state.value.current)
        assertEquals(0.seconds, engine.state.value.position)
        player.previous()
        assertEquals(tracks[0], player.state.value.current)
    }

    @Test
    fun loadFailureIsExposedAsError() = runTest {
        val player = controller()
        engine.failNextLoad = IllegalStateException("boom")
        player.play(tracks)
        assertNotNull(player.state.value.error)
    }

    @Test
    fun togglePauseAndResume() = runTest {
        val player = controller()
        player.play(tracks)
        player.togglePlayPause()
        assertEquals(EngineStatus.PAUSED, player.state.value.engine.status)
        player.togglePlayPause()
        assertEquals(EngineStatus.PLAYING, player.state.value.engine.status)
    }
}
