package space.wavedot.core.contract.ui

import kotlinx.coroutines.flow.StateFlow

data class RegisteredPluginUi(
    val pluginId: String,
    val pluginName: String,
    val ui: PluginUi,
)

interface UiExtensionRegistry {
    val extensions: StateFlow<List<RegisteredPluginUi>>

    fun register(extension: RegisteredPluginUi)

    fun unregister(pluginId: String)
}
