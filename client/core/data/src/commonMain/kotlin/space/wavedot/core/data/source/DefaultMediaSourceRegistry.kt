package space.wavedot.core.data.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.model.SourceId

class DefaultMediaSourceRegistry : MediaSourceRegistry {
    private val state = MutableStateFlow<List<MediaSource>>(emptyList())

    override val sources: StateFlow<List<MediaSource>> = state.asStateFlow()

    override fun register(source: MediaSource) = state.update { current ->
        current.filterNot { it.descriptor.id == source.descriptor.id } + source
    }

    override fun unregister(id: SourceId) = state.update { current ->
        current.filterNot { it.descriptor.id == id }
    }
}
