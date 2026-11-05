package space.wavedot.core.common

import kotlinx.coroutines.flow.Flow

interface KeyValueStore {
    suspend fun get(key: String): String?

    suspend fun set(key: String, value: String?)

    fun observe(key: String): Flow<String?>
}
