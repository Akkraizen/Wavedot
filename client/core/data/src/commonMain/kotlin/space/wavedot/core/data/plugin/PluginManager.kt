package space.wavedot.core.data.plugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.wavedot.core.common.Logger
import space.wavedot.core.common.NoopLogger
import space.wavedot.core.common.warn
import space.wavedot.core.contract.LyricsProviderRegistry
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.contract.ui.RegisteredPluginUi
import space.wavedot.core.contract.ui.UiExtensionRegistry
import space.wavedot.core.model.SourceId
import space.wavedot.plugin.api.PLUGIN_API_VERSION
import space.wavedot.plugin.api.PluginContext
import space.wavedot.plugin.api.PluginManifest
import space.wavedot.plugin.api.WavedotPlugin

sealed interface PluginStatus {
    data object Unloaded : PluginStatus
    data class Loaded(
        val sources: List<SourceId>,
        val lyricsProviders: List<String> = emptyList(),
        val hasUi: Boolean = false,
    ) : PluginStatus
    data class Failed(val reason: String) : PluginStatus
}

data class PluginEntry(
    val manifest: PluginManifest,
    val status: PluginStatus,
)

fun interface PluginContextFactory {
    fun create(manifest: PluginManifest): PluginContext
}

class PluginManager(
    plugins: List<WavedotPlugin>,
    private val registry: MediaSourceRegistry,
    private val lyricsRegistry: LyricsProviderRegistry,
    private val uiRegistry: UiExtensionRegistry,
    private val contextFactory: PluginContextFactory,
    private val logger: Logger = NoopLogger,
) {
    private val plugins = plugins.associateBy { it.manifest.id }
    private val mutex = Mutex()
    private val state = MutableStateFlow(plugins.map { PluginEntry(it.manifest, PluginStatus.Unloaded) })

    val entries: StateFlow<List<PluginEntry>> = state.asStateFlow()

    init {
        require(this.plugins.size == plugins.size) { "Duplicate plugin ids" }
    }

    suspend fun loadAll() = plugins.keys.forEach { load(it) }

    suspend fun reload(id: String) {
        unload(id)
        load(id)
    }

    suspend fun load(id: String): PluginStatus = mutex.withLock {
        val plugin = plugins[id] ?: return@withLock PluginStatus.Failed("Unknown plugin $id")
        val current = statusOf(id)
        if (current is PluginStatus.Loaded) return@withLock current
        val status = try {
            check(plugin.manifest.apiVersion == PLUGIN_API_VERSION) {
                "Plugin API ${plugin.manifest.apiVersion} is not supported (host: $PLUGIN_API_VERSION)"
            }
            val contribution = plugin.onLoad(contextFactory.create(plugin.manifest))
            contribution.sources.forEach(registry::register)
            contribution.lyricsProviders.forEach(lyricsRegistry::register)
            val hasUi = !contribution.ui.isEmpty
            if (hasUi) uiRegistry.register(RegisteredPluginUi(id, plugin.manifest.name, contribution.ui))
            PluginStatus.Loaded(
                sources = contribution.sources.map { it.descriptor.id },
                lyricsProviders = contribution.lyricsProviders.map { it.id },
                hasUi = hasUi,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger.warn(TAG, "Plugin $id failed to load", t)
            PluginStatus.Failed(t.message ?: t::class.simpleName.orEmpty())
        }
        setStatus(id, status)
        status
    }

    suspend fun unload(id: String) = mutex.withLock {
        val plugin = plugins[id] ?: return@withLock
        val current = statusOf(id)
        if (current is PluginStatus.Loaded) {
            current.sources.forEach(registry::unregister)
            current.lyricsProviders.forEach(lyricsRegistry::unregister)
            uiRegistry.unregister(id)
            runCatching { plugin.onUnload() }.onFailure { logger.warn(TAG, "Plugin $id failed to unload", it) }
        }
        setStatus(id, PluginStatus.Unloaded)
    }

    private fun statusOf(id: String): PluginStatus =
        state.value.first { it.manifest.id == id }.status

    private fun setStatus(id: String, status: PluginStatus) = state.update { list ->
        list.map { if (it.manifest.id == id) it.copy(status = status) else it }
    }

    private companion object {
        const val TAG = "PluginManager"
    }
}
