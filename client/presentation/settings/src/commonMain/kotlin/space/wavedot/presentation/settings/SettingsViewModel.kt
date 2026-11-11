package space.wavedot.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.wavedot.core.common.toWavedotError

data class SettingsUiState(
    val plugins: List<PluginInfo> = emptyList(),
    val hubConnected: Boolean = false,
    val musicFolders: List<String>? = null,
    val appVersion: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val accentId: String? = null,
    val section: SettingsSection? = null,
) {
    val sections: List<SettingsSection>
        get() = SettingsSection.entries.filter { it != SettingsSection.LIBRARY || musicFolders != null }

    val sourcePlugins: List<PluginInfo> get() = plugins.filter { it.group == PluginGroup.SOURCES }

    val lyricsPlugins: List<PluginInfo> get() = plugins.filter { it.group == PluginGroup.LYRICS }
}

sealed interface SettingsIntent {
    data class SavePluginValue(val pluginId: String, val key: String, val value: String) : SettingsIntent
    data class ConnectHub(val url: String) : SettingsIntent
    data object DisconnectHub : SettingsIntent
    data object SyncNow : SettingsIntent
    data object CheckUpdates : SettingsIntent
    data class AddFolder(val path: String) : SettingsIntent
    data class RemoveFolder(val path: String) : SettingsIntent
    data object DismissMessage : SettingsIntent
    data class SelectAccent(val id: String) : SettingsIntent
    data class OpenSection(val section: SettingsSection) : SettingsIntent
    data object CloseSection : SettingsIntent
}

class SettingsViewModel(private val backend: SettingsBackend) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val section = MutableStateFlow<SettingsSection?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        combine(
            backend.plugins,
            backend.hubConnected,
            backend.musicFolders ?: flowOf(null),
            busy,
            message,
        ) { plugins, hub, folders, isBusy, text ->
            SettingsUiState(plugins, hub, folders, backend.appVersion, isBusy, text)
        },
        backend.accentId,
        section,
    ) { state, accent, opened ->
        state.copy(accentId = accent, section = opened?.takeIf { it in state.sections })
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(appVersion = backend.appVersion),
    )

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SavePluginValue -> run("Saved") {
                backend.updatePluginValue(intent.pluginId, intent.key, intent.value.trim())
            }
            is SettingsIntent.ConnectHub -> run("Connected to hub") { backend.connectHub(intent.url.trim()) }
            SettingsIntent.DisconnectHub -> run("Disconnected") { backend.disconnectHub() }
            SettingsIntent.SyncNow -> run(null) {
                val report = backend.syncNow()
                message.value = buildString {
                    append("Imported ${report.imported}, sent ${report.pushed}, received ${report.pulled}")
                    if (report.errors.isNotEmpty()) append("\n").append(report.errors.joinToString("\n"))
                }
            }
            SettingsIntent.CheckUpdates -> run(null) {
                val update = backend.checkForUpdates()
                message.value = update?.let { "Version ${it.version} is available" } ?: "You are up to date"
            }
            is SettingsIntent.AddFolder -> run(null) {
                val current = state.value.musicFolders.orEmpty()
                if (intent.path.isNotBlank() && intent.path !in current) backend.setMusicFolders(current + intent.path.trim())
                message.value = "Found ${backend.rescanLocal()} tracks"
            }
            is SettingsIntent.RemoveFolder -> run(null) {
                backend.setMusicFolders(state.value.musicFolders.orEmpty() - intent.path)
                message.value = "Found ${backend.rescanLocal()} tracks"
            }
            SettingsIntent.DismissMessage -> message.value = null
            is SettingsIntent.SelectAccent -> viewModelScope.launch { backend.setAccent(intent.id) }
            is SettingsIntent.OpenSection -> section.value = intent.section
            SettingsIntent.CloseSection -> section.value = null
        }
    }

    private fun run(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
                success?.let { message.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                message.value = t.toWavedotError().message
            } finally {
                busy.value = false
            }
        }
    }
}
