package space.wavedot.plugin.api

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import space.wavedot.core.common.Logger

interface PluginContext {
    val pluginId: String
    val httpClient: HttpClient
    val settings: PluginStore
    val secrets: PluginStore
    val logger: Logger
}

interface PluginStore {
    suspend fun get(key: String): String?

    suspend fun set(key: String, value: String)

    suspend fun delete(key: String)

    fun observe(key: String): Flow<String?>
}
