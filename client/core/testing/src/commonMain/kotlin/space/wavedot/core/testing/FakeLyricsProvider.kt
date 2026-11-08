package space.wavedot.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import space.wavedot.core.contract.LyricsProvider
import space.wavedot.core.contract.LyricsProviderRegistry
import space.wavedot.core.contract.LyricsRequest
import space.wavedot.core.model.lyrics.Lyrics

class FakeLyricsProvider(
    override val id: String,
    var result: Lyrics? = null,
    var failure: Throwable? = null,
) : LyricsProvider {
    override val displayName: String = id
    var calls = 0

    override suspend fun find(request: LyricsRequest): Lyrics? {
        calls++
        failure?.let { throw it }
        return result
    }
}

class TestLyricsRegistry(vararg initial: LyricsProvider) : LyricsProviderRegistry {
    private val state = MutableStateFlow(initial.toList())
    override val providers: StateFlow<List<LyricsProvider>> = state

    override fun register(provider: LyricsProvider) = state.update { it + provider }

    override fun unregister(id: String) = state.update { list -> list.filterNot { it.id == id } }
}
