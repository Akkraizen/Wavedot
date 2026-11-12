package space.wavedot.plugin.radiobrowser

import space.wavedot.plugin.api.PluginContext
import space.wavedot.plugin.api.PluginContribution
import space.wavedot.plugin.api.PluginManifest
import space.wavedot.plugin.api.WavedotPlugin

class RadioBrowserPlugin(
    private val baseUrl: String = RadioBrowserApi.DEFAULT_BASE_URL,
) : WavedotPlugin {
    override val manifest: PluginManifest = PluginManifest(
        id = ID,
        name = "Internet radio",
        version = "0.1.0",
        description = "Live stations from the community Radio Browser directory. No account needed.",
    )

    override suspend fun onLoad(context: PluginContext): PluginContribution {
        val api = RadioBrowserApi(context.httpClient, baseUrl)
        return PluginContribution(
            sources = listOf(RadioBrowserSource(api)),
            ui = RadioBrowserUi(api, context.settings).build(),
        )
    }

    companion object {
        const val ID: String = "radio-browser"
    }
}
