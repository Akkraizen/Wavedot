package space.wavedot.core.data.plugin

import space.wavedot.core.common.KeyValueStore
import io.ktor.client.HttpClient
import space.wavedot.core.common.Logger
import space.wavedot.plugin.api.PluginContext
import space.wavedot.plugin.api.PluginManifest
import space.wavedot.plugin.api.PluginStore

class DefaultPluginContextFactory(
    private val httpClient: HttpClient,
    private val settings: KeyValueStore,
    private val secrets: KeyValueStore,
    private val logger: Logger,
) : PluginContextFactory {
    override fun create(manifest: PluginManifest): PluginContext = object : PluginContext {
        override val pluginId: String = manifest.id
        override val httpClient: HttpClient = this@DefaultPluginContextFactory.httpClient
        override val settings: PluginStore = NamespacedPluginStore(this@DefaultPluginContextFactory.settings, manifest.id)
        override val secrets: PluginStore = NamespacedPluginStore(this@DefaultPluginContextFactory.secrets, manifest.id)
        override val logger: Logger = this@DefaultPluginContextFactory.logger
    }
}
