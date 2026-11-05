package space.wavedot.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.model.SourceId

class TestRegistry(vararg initial: MediaSource) : MediaSourceRegistry {
    private val state = MutableStateFlow(initial.toList())
    override val sources: StateFlow<List<MediaSource>> = state

    override fun register(source: MediaSource) = state.update { list ->
        list.filterNot { it.descriptor.id == source.descriptor.id } + source
    }

    override fun unregister(id: SourceId) = state.update { list -> list.filterNot { it.descriptor.id == id } }
}
