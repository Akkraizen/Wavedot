package space.wavedot.infra.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import space.wavedot.core.domain.lyrics.CachedLyrics
import space.wavedot.core.domain.lyrics.LyricsCache
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.lyrics.LyricLine
import space.wavedot.core.model.lyrics.Lyrics
import kotlin.time.Duration.Companion.milliseconds

class SqlLyricsCache(
    private val database: WavedotDatabase,
    private val dispatcher: CoroutineDispatcher,
) : LyricsCache {
    override suspend fun get(id: MediaId): CachedLyrics? = withContext(dispatcher) {
        database.lyricsCacheQueries.select(id.source.value, id.key).executeAsOneOrNull()?.let { row ->
            val lyrics = if (row.found == 0L) null else Lyrics(
                lines = LineCodec.decode(row.body.orEmpty()),
                synced = row.synced != 0L,
                provider = row.provider.orEmpty(),
                credits = row.credits,
            )
            CachedLyrics(lyrics, row.fetched_at)
        }
    }

    override suspend fun put(id: MediaId, lyrics: Lyrics?, fetchedAt: Long) {
        withContext(dispatcher) {
            database.lyricsCacheQueries.upsert(
                source = id.source.value,
                media_key = id.key,
                found = if (lyrics == null) 0 else 1,
                synced = if (lyrics?.synced == true) 1 else 0,
                provider = lyrics?.provider,
                credits = lyrics?.credits,
                body = lyrics?.let { LineCodec.encode(it.lines) },
                fetched_at = fetchedAt,
            )
        }
    }
}

internal object LineCodec {
    private const val NO_TIME = "-"

    fun encode(lines: List<LyricLine>): String =
        lines.joinToString("\n") { line ->
            "${line.start?.inWholeMilliseconds ?: NO_TIME}\t${line.text.replace('\n', ' ')}"
        }

    fun decode(body: String): List<LyricLine> =
        if (body.isEmpty()) emptyList()
        else body.split('\n').map { entry ->
            val time = entry.substringBefore('\t')
            LyricLine(time.toLongOrNull()?.milliseconds, entry.substringAfter('\t', ""))
        }
}
