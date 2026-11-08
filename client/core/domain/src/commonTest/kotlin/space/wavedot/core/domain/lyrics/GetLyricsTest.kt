package space.wavedot.core.domain.lyrics

import kotlinx.coroutines.test.runTest
import space.wavedot.core.contract.SourceKind
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.lyrics.LrcParser
import space.wavedot.core.model.lyrics.Lyrics
import space.wavedot.core.testing.FakeLyricsProvider
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.InMemoryLyricsCache
import space.wavedot.core.testing.TestLyricsRegistry
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetLyricsTest {
    private val track = TestTracks.track("1")
    private val synced = LrcParser.parse("[00:01.00]Hello", "external")
    private val plain = Lyrics.plain("Hello", "native")
    private val cache = InMemoryLyricsCache()
    private var now = 1_000L

    private fun getLyrics(source: FakeMediaSource, vararg external: FakeLyricsProvider) =
        GetLyrics(TestRegistry(source), TestLyricsRegistry(*external), cache, { now })

    @Test
    fun prefersSyncedLyricsOverEarlierPlainOnes() = runTest {
        val native = FakeLyricsProvider("native", plain)
        val external = FakeLyricsProvider("external", synced)
        val result = getLyrics(FakeMediaSource(tracks = listOf(track), lyrics = native), external)(track)
        assertEquals(synced, assertIs<LyricsResult.Found>(result).lyrics)
        assertEquals(synced, cache.entries[track.id]?.lyrics)
    }

    @Test
    fun nativeSyncedLyricsSkipExternalProviders() = runTest {
        val native = FakeLyricsProvider("native", synced)
        val external = FakeLyricsProvider("external", plain)
        getLyrics(FakeMediaSource(tracks = listOf(track), lyrics = native), external)(track)
        assertEquals(0, external.calls)
    }

    @Test
    fun fallsBackToPlainLyrics() = runTest {
        val result = getLyrics(FakeMediaSource(lyrics = FakeLyricsProvider("native", plain)), FakeLyricsProvider("external"))(track)
        assertEquals(plain, assertIs<LyricsResult.Found>(result).lyrics)
    }

    @Test
    fun localSourcesAreUnsupported() = runTest {
        val external = FakeLyricsProvider("external", synced)
        val local = FakeMediaSource(id = SourceId("local"), kind = SourceKind.LOCAL)
        val localTrack = TestTracks.track("1", source = SourceId("local"))
        assertEquals(LyricsResult.Unsupported, getLyrics(local, external)(localTrack))
        assertEquals(0, external.calls)
    }

    @Test
    fun cachesMissesForAWhile() = runTest {
        val external = FakeLyricsProvider("external")
        val getLyrics = getLyrics(FakeMediaSource(), external)
        assertEquals(LyricsResult.NotFound, getLyrics(track))
        assertEquals(LyricsResult.NotFound, getLyrics(track))
        assertEquals(1, external.calls)
        now += 4L * 24 * 60 * 60 * 1000
        getLyrics(track)
        assertEquals(2, external.calls)
    }

    @Test
    fun failuresAreReportedAndNotCached() = runTest {
        val external = FakeLyricsProvider("external", failure = IllegalStateException("offline"))
        val result = getLyrics(FakeMediaSource(), external)(track)
        assertIs<LyricsResult.Failed>(result)
        assertTrue(cache.entries.isEmpty())
    }
}
