package space.wavedot.presentation.settings

import kotlinx.coroutines.flow.Flow
import space.wavedot.core.domain.sync.SyncReport
import space.wavedot.core.domain.update.AvailableUpdate

data class PluginSecretField(
    val key: String,
    val title: String,
    val secret: Boolean,
    val hint: String = "",
    val value: String? = null,
    val configured: Boolean = false,
)

enum class PluginGroup { SOURCES, LYRICS }

enum class PluginState { ACTIVE, NEEDS_SETUP, DISABLED, FAILED }

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val state: PluginState,
    val fields: List<PluginSecretField>,
    val group: PluginGroup = PluginGroup.SOURCES,
    val failure: String? = null,
) {
    val active: Boolean get() = state == PluginState.ACTIVE

    val status: String
        get() = when (state) {
            PluginState.ACTIVE -> "Active"
            PluginState.NEEDS_SETUP -> "Needs setup"
            PluginState.DISABLED -> "Disabled"
            PluginState.FAILED -> "Error: ${failure.orEmpty()}"
        }
}

interface SettingsBackend {
    val plugins: Flow<List<PluginInfo>>
    val hubConnected: Flow<Boolean>
    val musicFolders: Flow<List<String>>?
    val appVersion: String
    val accentId: Flow<String?>

    suspend fun updatePluginValue(pluginId: String, key: String, value: String)

    suspend fun connectHub(url: String, registrationToken: String)

    suspend fun disconnectHub()

    suspend fun deleteHubAccount()

    suspend fun syncNow(): SyncReport

    suspend fun checkForUpdates(): AvailableUpdate?

    suspend fun setMusicFolders(folders: List<String>)

    suspend fun rescanLocal(): Int

    suspend fun setAccent(id: String)
}
