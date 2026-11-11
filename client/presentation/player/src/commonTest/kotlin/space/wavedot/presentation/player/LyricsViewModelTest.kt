package space.wavedot.presentation.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import space.wavedot.core.contract.SourceKind
import space.wavedot.core.domain.lyrics.GetLyrics
import space.wavedot.core.domain.player.FixedPlaybackPreferences
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.domain.player.ResolveStream
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.lyrics.LrcParser
import space.wavedot.core.testing.FakeAudioEngine
import space.wavedot.core.testing.FakeLyricsProvider
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.InMemoryLyricsCache
import space.wavedot.core.testing.TestLyricsRegistry
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val engine = FakeAudioEngine()
    private val lyrics = LrcParser.parse("[00:01.00]One\n[00:05.00]Two\n[00:09.00]Three", "LRCLIB")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun followsPlaybackPositionAndSeeksToLines() = runTest(dispatcher) {
        val tracks = TestTracks.tracks(2)
        val registry = TestRegistry(FakeMediaSource(tracks = tracks))
        val controller = PlayerController(engine, ResolveStream(registry), FixedPlaybackPreferences(), backgroundScope)
        val viewModel = LyricsViewModel(
            controller,
            GetLyrics(registry, TestLyricsRegistry(FakeLyricsProvider("lrclib", lyrics)), InMemoryLyricsCache(), { 0L }),
        )
        backgroundScope.launch { viewModel.state.collect {} }

        controller.play(tracks)
        assertIs<LyricsContent.Ready>(viewModel.state.first { it.content is LyricsContent.Ready }.content)

        engine.seekTo(6.seconds)
        assertEquals(1, viewModel.state.first { it.activeIndex == 1 }.activeIndex)

        viewModel.onIntent(LyricsIntent.SeekToLine(lyrics.lines[2]))
        assertEquals(9.seconds, engine.state.value.position)
        assertEquals(2, viewModel.state.first { it.activeIndex == 2 }.activeIndex)
    }

    @Test
    fun localTracksAreUnsupported() = runTest(dispatcher) {
        val local = SourceId("local")
        val tracks = TestTracks.tracks(1, local)
        val registry = TestRegistry(FakeMediaSource(id = local, tracks = tracks, kind = SourceKind.LOCAL))
        val controller = PlayerController(engine, ResolveStream(registry), FixedPlaybackPreferences(), backgroundScope)
        val provider = FakeLyricsProvider("lrclib", lyrics)
        val viewModel = LyricsViewModel(controller, GetLyrics(registry, TestLyricsRegistry(provider), InMemoryLyricsCache(), { 0L }))
        backgroundScope.launch { viewModel.state.collect {} }

        controller.play(tracks)
        viewModel.state.first { it.content == LyricsContent.Unsupported }
        assertEquals(0, provider.calls)
    }
}
