package space.wavedot.plugin.subsonic

import io.ktor.http.Url
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.common.info
import space.wavedot.core.common.warn
import space.wavedot.plugin.api.PluginContext
import space.wavedot.plugin.api.PluginContribution
import space.wavedot.plugin.api.PluginManifest
import space.wavedot.plugin.api.PluginSettingSpec
import space.wavedot.plugin.api.WavedotPlugin

class SubsonicPlugin : WavedotPlugin {
    override val manifest: PluginManifest = PluginManifest(
        id = ID,
        name = "Subsonic",
        version = "0.1.0",
        description = "Your own music server: Navidrome, Gonic, Airsonic, Ampache or any Subsonic-compatible server.",
        settings = listOf(
            PluginSettingSpec(SERVER_KEY, "Server URL", hint = "https://music.example.com"),
            PluginSettingSpec(USERNAME_KEY, "Username"),
            PluginSettingSpec(PASSWORD_KEY, "Password", secret = true),
        ),
    )

    override suspend fun onLoad(context: PluginContext): PluginContribution {
        val server = context.settings.get(SERVER_KEY)?.trim()?.takeIf { it.isNotEmpty() }
        val username = context.settings.get(USERNAME_KEY)?.trim()?.takeIf { it.isNotEmpty() }
        val password = context.secrets.get(PASSWORD_KEY)?.takeIf { it.isNotEmpty() }
        if (server == null || username == null || password == null) {
            context.logger.info(ID, "Server, username or password missing, source is disabled")
            return PluginContribution()
        }
        val api = SubsonicApi(context.httpClient, SubsonicConfig(server, username, password))
        try {
            api.ping()
        } catch (e: WavedotException) {
            if (e.error is WavedotError.Unauthorized) throw e
            context.logger.warn(ID, "Server is unreachable right now", e)
        }
        return PluginContribution(listOf(SubsonicSource(api, displayName(server))))
    }

    companion object {
        const val ID: String = "subsonic"
        const val SERVER_KEY: String = "server"
        const val USERNAME_KEY: String = "username"
        const val PASSWORD_KEY: String = "password"

        internal fun displayName(server: String): String {
            val host = runCatching { Url(server.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() }
            return if (host == null) "Subsonic" else "Subsonic · $host"
        }
    }
}
