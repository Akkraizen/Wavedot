package space.wavedot.plugin.api

import space.wavedot.core.contract.LyricsProvider
import space.wavedot.core.contract.MediaSource

const val PLUGIN_API_VERSION: Int = 1

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val apiVersion: Int = PLUGIN_API_VERSION,
    val settings: List<PluginSettingSpec> = emptyList(),
)

data class PluginSettingSpec(
    val key: String,
    val title: String,
    val secret: Boolean = false,
)

data class PluginContribution(
    val sources: List<MediaSource> = emptyList(),
    val lyricsProviders: List<LyricsProvider> = emptyList(),
)

interface WavedotPlugin {
    val manifest: PluginManifest

    suspend fun onLoad(context: PluginContext): PluginContribution

    suspend fun onUnload() = Unit
}
