package space.wavedot.core.contract

import kotlinx.coroutines.flow.StateFlow
import space.wavedot.core.model.Track
import space.wavedot.core.model.lyrics.Lyrics

data class LyricsRequest(val track: Track)

interface LyricsProvider {
    val id: String
    val displayName: String

    suspend fun find(request: LyricsRequest): Lyrics?
}

interface LyricsProviderRegistry {
    val providers: StateFlow<List<LyricsProvider>>

    fun register(provider: LyricsProvider)

    fun unregister(id: String)
}
