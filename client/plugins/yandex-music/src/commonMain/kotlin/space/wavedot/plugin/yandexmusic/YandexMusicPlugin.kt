package space.wavedot.plugin.yandexmusic

import space.wavedot.core.common.SystemTimeProvider
import space.wavedot.core.common.TimeProvider
import space.wavedot.core.common.info
import space.wavedot.plugin.api.PluginContext
import space.wavedot.plugin.api.PluginContribution
import space.wavedot.plugin.api.PluginManifest
import space.wavedot.plugin.api.PluginSettingSpec
import space.wavedot.plugin.api.WavedotPlugin
import space.wavedot.plugin.yandexmusic.api.ClientProfile
import space.wavedot.plugin.yandexmusic.api.YandexMusicApi

class YandexMusicPlugin(
    private val time: TimeProvider = SystemTimeProvider,
    private val baseUrl: String = YandexMusicApi.DEFAULT_BASE_URL,
    private val profile: ClientProfile = ClientProfile.ANDROID,
) : WavedotPlugin {
    override val manifest: PluginManifest = PluginManifest(
        id = ID,
        name = "Yandex Music",
        version = "0.1.0",
        description = "Streams tracks, search and likes from Yandex Music using your own account token.",
        settings = listOf(PluginSettingSpec(TOKEN_KEY, "OAuth token", secret = true)),
    )

    override suspend fun onLoad(context: PluginContext): PluginContribution {
        val token = context.secrets.get(TOKEN_KEY)?.takeIf { it.isNotBlank() }
        if (token == null) {
            context.logger.info(ID, "No token configured, source is disabled")
            return PluginContribution()
        }
        val api = YandexMusicApi(context.httpClient, token, profile, time, baseUrl)
        return PluginContribution(listOf(YandexMusicSource(api, context.httpClient)))
    }

    companion object {
        const val ID: String = "yandex-music"
        const val TOKEN_KEY: String = "token"
    }
}
