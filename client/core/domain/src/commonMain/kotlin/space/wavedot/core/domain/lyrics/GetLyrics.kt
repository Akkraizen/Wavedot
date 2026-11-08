package space.wavedot.core.domain.lyrics

import kotlinx.coroutines.CancellationException
import space.wavedot.core.common.Logger
import space.wavedot.core.common.NoopLogger
import space.wavedot.core.common.TimeProvider
import space.wavedot.core.common.toWavedotError
import space.wavedot.core.common.warn
import space.wavedot.core.contract.LyricsProviderRegistry
import space.wavedot.core.contract.LyricsRequest
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.contract.SourceKind
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import space.wavedot.core.model.lyrics.Lyrics

sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics) : LyricsResult
    data object NotFound : LyricsResult
    data object Unsupported : LyricsResult
    data class Failed(val message: String) : LyricsResult
}

data class CachedLyrics(
    val lyrics: Lyrics?,
    val fetchedAt: Long,
)

interface LyricsCache {
    suspend fun get(id: MediaId): CachedLyrics?

    suspend fun put(id: MediaId, lyrics: Lyrics?, fetchedAt: Long)
}

class GetLyrics(
    private val sources: MediaSourceRegistry,
    private val providers: LyricsProviderRegistry,
    private val cache: LyricsCache,
    private val time: TimeProvider,
    private val logger: Logger = NoopLogger,
    private val missTtlMillis: Long = DEFAULT_MISS_TTL_MILLIS,
) {
    suspend operator fun invoke(track: Track): LyricsResult {
        val source = sources.find(track.id.source)
        if (source == null || source.descriptor.kind != SourceKind.STREAMING) return LyricsResult.Unsupported

        val now = time.nowMillis()
        cache.get(track.id)?.let { cached ->
            cached.lyrics?.let { return LyricsResult.Found(it) }
            if (now - cached.fetchedAt < missTtlMillis) return LyricsResult.NotFound
        }

        val candidates = listOfNotNull(source.lyrics) + providers.providers.value
        val request = LyricsRequest(track)
        var plain: Lyrics? = null
        var failure: String? = null
        for (provider in candidates) {
            val lyrics = try {
                provider.find(request)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.warn(TAG, "${provider.displayName} failed for ${track.id}", t)
                failure = failure ?: t.toWavedotError().message
                null
            }
            if (lyrics == null || lyrics.isEmpty) continue
            if (lyrics.synced) return remember(track.id, lyrics, now)
            if (plain == null) plain = lyrics
        }
        plain?.let { return remember(track.id, it, now) }
        if (failure != null) return LyricsResult.Failed(failure)
        cache.put(track.id, null, now)
        return LyricsResult.NotFound
    }

    private suspend fun remember(id: MediaId, lyrics: Lyrics, now: Long): LyricsResult {
        cache.put(id, lyrics, now)
        return LyricsResult.Found(lyrics)
    }

    private companion object {
        const val TAG = "GetLyrics"
        const val DEFAULT_MISS_TTL_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}

