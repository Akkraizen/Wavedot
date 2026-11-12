package space.wavedot.core.domain.player

import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ResolveStreamTest {
    private val resolver = ResolveStream(TestRegistry())

    @Test
    fun picksBestSupportedQualityNotAboveRequested() {
        val supported = setOf(AudioQuality.LOW, AudioQuality.NORMAL)
        assertEquals(AudioQuality.NORMAL, resolver.pickQuality(AudioQuality.LOSSLESS, supported))
        assertEquals(AudioQuality.LOW, resolver.pickQuality(AudioQuality.LOW, supported))
    }

    @Test
    fun fallsBackToLowestWhenNothingIsLowerOrEqual() {
        assertEquals(AudioQuality.HIGH, resolver.pickQuality(AudioQuality.LOW, setOf(AudioQuality.HIGH, AudioQuality.LOSSLESS)))
    }

    @Test
    fun passesHttpStreamsAndRefusesEverythingElse() = runTest {
        val source = FakeMediaSource(tracks = TestTracks.tracks(1))
        val resolve = ResolveStream(TestRegistry(source))
        val track = source.tracks.first()

        assertIs<StreamSource.Remote>(resolve(track, AudioQuality.NORMAL))

        val unsafe = listOf(
            StreamSource.Remote("file:///etc/passwd"),
            StreamSource.Remote("concat:/a.mp3|/b.mp3"),
            StreamSource.Remote("/etc/passwd"),
            StreamSource.Remote("rtmp://radio.test/live"),
            StreamSource.Remote("https://radio.test/live", headers = mapOf("X-A" to "1\r\nHost: evil.test")),
        )
        unsafe.forEach { stream ->
            source.streamOverride = stream
            val error = assertFailsWith<WavedotException>(stream.url) { resolve(track, AudioQuality.NORMAL) }
            assertIs<WavedotError.Invalid>(error.error)
        }

        source.streamOverride = StreamSource.LocalFile("/music/a.mp3")
        assertIs<StreamSource.LocalFile>(resolve(track, AudioQuality.NORMAL))
    }
}
