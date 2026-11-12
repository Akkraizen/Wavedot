package space.wavedot.plugin.audius

import space.wavedot.plugin.api.PluginContext
import space.wavedot.plugin.api.PluginContribution
import space.wavedot.plugin.api.PluginManifest
import space.wavedot.plugin.api.WavedotPlugin

class AudiusPlugin(
    private val baseUrl: String = AudiusApi.DEFAULT_BASE_URL,
) : WavedotPlugin {
    override val manifest: PluginManifest = PluginManifest(
        id = ID,
        name = "Audius",
        version = "0.1.0",
        description = "Free streaming from the open Audius catalog: tracks, artists and playlists. No account needed.",
    )

    override suspend fun onLoad(context: PluginContext): PluginContribution {
        val api = AudiusApi(context.httpClient, baseUrl)
        return PluginContribution(sources = listOf(AudiusSource(api)), ui = AudiusUi(api).build())
    }

    companion object {
        const val ID: String = "audius"
    }
}
