package space.wavedot.infra.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import space.wavedot.core.model.lyrics.LrcParser
import space.wavedot.core.model.lyrics.Lyrics
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlLyricsCacheTest {
    private val cache = SqlLyricsCache(createJvmDatabase(null), Dispatchers.Unconfined)
    private val id = TestTracks.track("1").id

    @Test
    fun roundTripsSyncedAndPlainLyrics() = runTest {
        val synced = LrcParser.parse("[00:01.50]First\n[00:03.00]Second", "LRCLIB", "Writer")
        cache.put(id, synced, 10)
        assertEquals(synced, cache.get(id)?.lyrics)

        val plain = Lyrics.plain("One\n\nTwo", "Yandex Music")
        cache.put(id, plain, 20)
        val cached = cache.get(id)!!
        assertEquals(plain, cached.lyrics)
        assertEquals(20, cached.fetchedAt)
    }

    @Test
    fun remembersMisses() = runTest {
        assertNull(cache.get(id))
        cache.put(id, null, 5)
        val cached = cache.get(id)!!
        assertNull(cached.lyrics)
        assertEquals(5, cached.fetchedAt)
    }
}
