package space.wavedot.feature.settings

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

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
    val active: Boolean,
    val fields: List<PluginSecretField>,
    val group: PluginGroup = PluginGroup.SOURCES,
)

interface SettingsBackend {
    val plugins: Flow<List<PluginInfo>>
    val hubConnected: Flow<Boolean>
    val musicFolders: Flow<List<String>>?
    val appVersion: String
    val accentId: Flow<String>
    val systemAccentAvailable: Boolean

    suspend fun updatePluginValue(pluginId: String, key: String, value: String)

    suspend fun connectHub(url: String)

    suspend fun disconnectHub()

    suspend fun syncNow(): SyncReport

    suspend fun checkForUpdates(): AvailableUpdate?

    suspend fun setMusicFolders(folders: List<String>)

    suspend fun rescanLocal(): Int

    suspend fun setAccent(id: String)
}
