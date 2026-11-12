package space.wavedot.app.shell

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.wavedot.core.common.KeyValueStore
import space.wavedot.core.data.plugin.NamespacedPluginStore
import space.wavedot.core.data.plugin.PluginManager
import space.wavedot.core.data.plugin.PluginStatus
import space.wavedot.core.domain.library.LocalLibrary
import space.wavedot.core.domain.sync.SyncLibrary
import space.wavedot.core.domain.sync.SyncReport
import space.wavedot.core.domain.update.AvailableUpdate
import space.wavedot.core.domain.update.CheckForUpdates
import space.wavedot.infra.hub.HubClient
import space.wavedot.infra.hub.HubSettings
import space.wavedot.plugin.api.PluginFeature
import space.wavedot.presentation.settings.PluginGroup
import space.wavedot.presentation.settings.PluginInfo
import space.wavedot.presentation.settings.PluginSecretField
import space.wavedot.presentation.settings.PluginState
import space.wavedot.presentation.settings.SettingsBackend

private val PluginStatus.Loaded.contributesNothing: Boolean
    get() = sources.isEmpty() && lyricsProviders.isEmpty()

class DefaultSettingsBackend(
    private val pluginManager: PluginManager,
    private val settingsStore: KeyValueStore,
    private val secretsStore: KeyValueStore,
    private val hubClient: HubClient,
    hubSettings: HubSettings,
    private val syncLibrary: () -> SyncLibrary,
    private val updateCheck: () -> CheckForUpdates,
    private val folders: MusicFolders,
    private val localLibrary: LocalLibrary,
    private val environment: PlatformEnvironment,
    private val appearance: AppearanceSettings,
) : SettingsBackend {
    override val plugins: Flow<List<PluginInfo>> = pluginManager.entries.map { entries ->
        entries.map { entry ->
            val status = entry.status
            PluginInfo(
                id = entry.manifest.id,
                name = entry.manifest.name,
                description = entry.manifest.description,
                state = when (status) {
                    is PluginStatus.Loaded -> if (status.contributesNothing) PluginState.NEEDS_SETUP else PluginState.ACTIVE
                    is PluginStatus.Failed -> PluginState.FAILED
                    PluginStatus.Unloaded -> PluginState.DISABLED
                },
                failure = (status as? PluginStatus.Failed)?.reason,
                group = if (PluginFeature.SOURCES in entry.manifest.features) PluginGroup.SOURCES else PluginGroup.LYRICS,
                fields = entry.manifest.settings.map { spec ->
                    val stored = store(entry.manifest.id, spec.secret).get(spec.key)
                    PluginSecretField(
                        key = spec.key,
                        title = spec.title,
                        secret = spec.secret,
                        hint = spec.hint,
                        value = stored.takeUnless { spec.secret },
                        configured = !stored.isNullOrEmpty(),
                    )
                },
            )
        }
    }

    override val hubConnected: Flow<Boolean> = hubSettings.observeConnected()

    override val musicFolders: Flow<List<String>>? =
        if (environment.supportsMusicFolders) {
            folders.folders
        } else {
            null
        }

    override val appVersion: String = environment.app.version

    override val accentId: Flow<String?> = appearance.accentId


    override suspend fun setAccent(id: String) = appearance.setAccent(id)

    override suspend fun updatePluginValue(pluginId: String, key: String, value: String) {
        val spec = pluginManager.entries.value
            .first { it.manifest.id == pluginId }
            .manifest.settings
            .first { it.key == key }
        val store = store(pluginId, spec.secret)
        val normalized = if (spec.secret) {
            value
        } else {
            value.trim()
        }
        if (normalized.isEmpty()) {
            store.delete(key)
        } else {
            store.set(key, normalized)
        }
        pluginManager.reload(pluginId)
    }

    private fun store(pluginId: String, secret: Boolean): NamespacedPluginStore {
        val delegate = if (secret) {
            secretsStore
        } else {
            settingsStore
        }
        return NamespacedPluginStore(delegate, pluginId)
    }

    override suspend fun connectHub(url: String, registrationToken: String) {
        hubClient.createAccount(url, environment.deviceName, environment.platform, registrationToken)
    }

    override suspend fun disconnectHub() = hubClient.disconnect()

    override suspend fun deleteHubAccount() = hubClient.deleteAccount()

    override suspend fun syncNow(): SyncReport = syncLibrary()()

    override suspend fun checkForUpdates(): AvailableUpdate? = updateCheck()()

    override suspend fun setMusicFolders(folders: List<String>) = this.folders.set(folders)

    override suspend fun rescanLocal(): Int = localLibrary.rescan()
}
