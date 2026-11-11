package space.wavedot.plugin.lrclib

import space.wavedot.plugin.api.PluginContext
import space.wavedot.plugin.api.PluginContribution
import space.wavedot.plugin.api.PluginFeature
import space.wavedot.plugin.api.PluginManifest
import space.wavedot.plugin.api.WavedotPlugin

class LrclibPlugin(private val baseUrl: String = LrclibLyricsProvider.DEFAULT_BASE_URL) : WavedotPlugin {
    override val manifest: PluginManifest = PluginManifest(
        id = ID,
        name = "LRCLIB",
        version = "0.1.0",
        description = "Free synced lyrics from lrclib.net for streaming tracks.",
        features = setOf(PluginFeature.LYRICS),
    )

    override suspend fun onLoad(context: PluginContext): PluginContribution =
        PluginContribution(lyricsProviders = listOf(LrclibLyricsProvider(context.httpClient, baseUrl)))

    companion object {
        const val ID: String = "lrclib"
    }
}
