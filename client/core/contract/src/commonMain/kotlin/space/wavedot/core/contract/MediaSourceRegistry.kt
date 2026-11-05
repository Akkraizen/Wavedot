package space.wavedot.core.contract

import kotlinx.coroutines.flow.StateFlow
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.model.SourceId

interface MediaSourceRegistry {
    val sources: StateFlow<List<MediaSource>>

    fun register(source: MediaSource)

    fun unregister(id: SourceId)

    fun find(id: SourceId): MediaSource? = sources.value.firstOrNull { it.descriptor.id == id }

    fun require(id: SourceId): MediaSource =
        find(id) ?: throw WavedotException(WavedotError.NotFound("source ${id.value}"))
}
