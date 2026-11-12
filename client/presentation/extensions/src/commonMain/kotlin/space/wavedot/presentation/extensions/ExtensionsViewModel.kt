package space.wavedot.presentation.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.wavedot.core.common.toWavedotError
import space.wavedot.core.contract.ui.PluginAccent
import space.wavedot.core.contract.ui.PluginTrackAction
import space.wavedot.core.contract.ui.RegisteredPluginUi
import space.wavedot.core.contract.ui.UiAction
import space.wavedot.core.contract.ui.UiEffect
import space.wavedot.core.contract.ui.UiExtensionRegistry
import space.wavedot.core.contract.ui.UiIcon
import space.wavedot.core.contract.ui.UiNode
import space.wavedot.core.contract.ui.UiTone
import space.wavedot.core.contract.ui.UiValueAction
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.model.Track

data class ExtensionScreen(
    val key: String,
    val title: String,
    val icon: UiIcon,
    val pluginName: String,
)

data class ExtensionSection(
    val key: String,
    val title: String,
    val pluginName: String,
)

data class ExtensionAccent(
    val id: String,
    val name: String,
    val argb: Long,
)

data class ExtensionTrackAction(
    val key: String,
    val label: String,
    val icon: UiIcon,
)

data class ExtensionsUiState(
    val screens: List<ExtensionScreen> = emptyList(),
    val librarySections: List<ExtensionSection> = emptyList(),
    val accents: List<ExtensionAccent> = emptyList(),
    val settingsPages: Set<String> = emptySet(),
    val message: String? = null,
)

sealed interface ExtensionsIntent {
    data class Run(val action: UiAction) : ExtensionsIntent
    data class RunWithValue<T>(val action: UiValueAction<T>, val value: T) : ExtensionsIntent
    data class RunTrackAction(val key: String, val track: Track) : ExtensionsIntent
    data object DismissMessage : ExtensionsIntent
}

class ExtensionsViewModel(
    private val registry: UiExtensionRegistry,
    private val player: PlayerController,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val contents = mutableMapOf<String, StateFlow<List<UiNode>>>()
    private var contentsFor: List<RegisteredPluginUi>? = null

    val state: StateFlow<ExtensionsUiState> = combine(registry.extensions, message) { extensions, text ->
        ExtensionsUiState(
            screens = extensions.flatMap { ext ->
                ext.ui.screens.map { ExtensionScreen(key(ext.pluginId, it.id), it.title, it.icon, ext.pluginName) }
            },
            librarySections = extensions.flatMap { ext ->
                ext.ui.librarySections.map { ExtensionSection(key(ext.pluginId, it.id), it.title, ext.pluginName) }
            },
            accents = extensions.flatMap { ext -> ext.ui.accents.map { accent(ext.pluginId, it) } },
            settingsPages = extensions.filter { it.ui.settings != null }.mapTo(mutableSetOf()) { it.pluginId },
            message = text,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ExtensionsUiState())

    fun screenContent(key: String): StateFlow<List<UiNode>> = content("screen:$key") {
        find(key) { ext, id -> ext.ui.screens.firstOrNull { it.id == id }?.content }
    }

    fun sectionContent(key: String): StateFlow<List<UiNode>> = content("section:$key") {
        find(key) { ext, id -> ext.ui.librarySections.firstOrNull { it.id == id }?.content }
    }

    fun settingsContent(pluginId: String): StateFlow<List<UiNode>> = content("settings:$pluginId") {
        registry.extensions.value.firstOrNull { it.pluginId == pluginId }?.ui?.settings
    }

    fun trackActions(track: Track): List<ExtensionTrackAction> =
        registry.extensions.value.flatMap { ext ->
            ext.ui.trackActions
                .filter { action -> runCatching { action.appliesTo(track) }.getOrDefault(false) }
                .map { ExtensionTrackAction(key(ext.pluginId, it.id), it.label, it.icon) }
        }

    fun onIntent(intent: ExtensionsIntent) {
        when (intent) {
            is ExtensionsIntent.Run -> execute { intent.action.run() }
            is ExtensionsIntent.RunWithValue<*> -> execute { runWithValue(intent) }
            is ExtensionsIntent.RunTrackAction -> trackAction(intent.key)?.let { action ->
                execute { action.action.run(intent.track) }
            }
            ExtensionsIntent.DismissMessage -> message.value = null
        }
    }

    private suspend fun <T> runWithValue(intent: ExtensionsIntent.RunWithValue<T>): UiEffect = intent.action.run(intent.value)

    private fun execute(block: suspend () -> UiEffect) {
        viewModelScope.launch {
            val effect = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                UiEffect.Message(t.toWavedotError().message)
            }
            when (effect) {
                UiEffect.None -> Unit
                is UiEffect.Message -> message.value = effect.text
                is UiEffect.Play -> if (effect.tracks.isNotEmpty()) {
                    player.play(effect.tracks, effect.startIndex.coerceIn(effect.tracks.indices))
                }
            }
        }
    }

    private fun trackAction(key: String): PluginTrackAction? =
        registry.extensions.value.firstNotNullOfOrNull { ext ->
            ext.ui.trackActions.firstOrNull { key(ext.pluginId, it.id) == key }
        }

    private fun content(cacheKey: String, source: () -> Flow<List<UiNode>>?): StateFlow<List<UiNode>> {
        val current = registry.extensions.value
        if (contentsFor !== current) {
            contents.clear()
            contentsFor = current
        }
        return contents.getOrPut(cacheKey) {
            (source() ?: flowOf(emptyList()))
                .onStart { emit(listOf(UiNode.Loading)) }
                .catch { emit(listOf(UiNode.Status(it.toWavedotError().message, UiTone.ERROR))) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(UiNode.Loading))
        }
    }

    private fun <T> find(key: String, pick: (RegisteredPluginUi, String) -> T?): T? {
        val pluginId = key.substringBefore(SEPARATOR)
        val id = key.substringAfter(SEPARATOR)
        val ext = registry.extensions.value.firstOrNull { it.pluginId == pluginId } ?: return null
        return pick(ext, id)
    }

    private fun accent(pluginId: String, accent: PluginAccent) = ExtensionAccent(key(pluginId, accent.id), accent.name, accent.argb)

    private fun key(pluginId: String, id: String) = "$pluginId$SEPARATOR$id"

    private companion object {
        const val SEPARATOR = "/"
    }
}
