package space.wavedot.core.data.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import space.wavedot.core.contract.LyricsProvider
import space.wavedot.core.contract.LyricsProviderRegistry

class DefaultLyricsProviderRegistry : LyricsProviderRegistry {
    private val state = MutableStateFlow<List<LyricsProvider>>(emptyList())

    override val providers: StateFlow<List<LyricsProvider>> = state.asStateFlow()

    override fun register(provider: LyricsProvider) = state.update { current ->
        current.filterNot { it.id == provider.id } + provider
    }

    override fun unregister(id: String) = state.update { current -> current.filterNot { it.id == id } }
}
