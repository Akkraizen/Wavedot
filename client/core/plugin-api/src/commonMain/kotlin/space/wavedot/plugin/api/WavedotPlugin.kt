package space.wavedot.plugin.api

import space.wavedot.core.contract.LyricsProvider
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.ui.PluginUi

const val PLUGIN_API_VERSION: Int = 1

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val apiVersion: Int = PLUGIN_API_VERSION,
    val settings: List<PluginSettingSpec> = emptyList(),
    val features: Set<PluginFeature> = setOf(PluginFeature.SOURCES),
)

enum class PluginFeature { SOURCES, LYRICS }

data class PluginSettingSpec(
    val key: String,
    val title: String,
    val secret: Boolean = false,
    val hint: String = "",
)

data class PluginContribution(
    val sources: List<MediaSource> = emptyList(),
    val lyricsProviders: List<LyricsProvider> = emptyList(),
    val ui: PluginUi = PluginUi(),
)

interface WavedotPlugin {
    val manifest: PluginManifest

    suspend fun onLoad(context: PluginContext): PluginContribution

    suspend fun onUnload() = Unit
}
