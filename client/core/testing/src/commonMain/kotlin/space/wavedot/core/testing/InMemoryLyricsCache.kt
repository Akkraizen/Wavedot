package space.wavedot.core.testing

import space.wavedot.core.domain.lyrics.CachedLyrics
import space.wavedot.core.domain.lyrics.LyricsCache
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.lyrics.Lyrics

class InMemoryLyricsCache : LyricsCache {
    val entries = mutableMapOf<MediaId, CachedLyrics>()

    override suspend fun get(id: MediaId): CachedLyrics? = entries[id]

    override suspend fun put(id: MediaId, lyrics: Lyrics?, fetchedAt: Long) {
        entries[id] = CachedLyrics(lyrics, fetchedAt)
    }
}
