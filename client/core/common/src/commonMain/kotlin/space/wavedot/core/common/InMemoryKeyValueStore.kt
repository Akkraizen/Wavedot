package space.wavedot.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    private val state = MutableStateFlow(initial)

    override suspend fun get(key: String): String? = state.value[key]

    override suspend fun set(key: String, value: String) = state.update { it + (key to value) }

    override suspend fun delete(key: String) = state.update { it - key }

    override fun observe(key: String): Flow<String?> =
        state
            .map { it[key] }
            .distinctUntilChanged()
}
