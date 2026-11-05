package space.wavedot.core.data.plugin

import kotlinx.coroutines.flow.Flow
import space.wavedot.core.common.KeyValueStore
import space.wavedot.plugin.api.PluginStore

class NamespacedPluginStore(
    private val delegate: KeyValueStore,
    private val namespace: String,
) : PluginStore {
    override suspend fun get(key: String): String? = delegate.get(scoped(key))

    override suspend fun set(key: String, value: String?) = delegate.set(scoped(key), value)

    override fun observe(key: String): Flow<String?> = delegate.observe(scoped(key))

    private fun scoped(key: String) = "plugin.$namespace.$key"
}
