package space.wavedot.core.data.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import space.wavedot.core.contract.ui.RegisteredPluginUi
import space.wavedot.core.contract.ui.UiExtensionRegistry

class DefaultUiExtensionRegistry : UiExtensionRegistry {
    private val state = MutableStateFlow<List<RegisteredPluginUi>>(emptyList())

    override val extensions: StateFlow<List<RegisteredPluginUi>> = state.asStateFlow()

    override fun register(extension: RegisteredPluginUi) = state.update { current ->
        val index = current.indexOfFirst { it.pluginId == extension.pluginId }
        if (index < 0) current + extension else current.toMutableList().apply { set(index, extension) }
    }

    override fun unregister(pluginId: String) = state.update { current -> current.filterNot { it.pluginId == pluginId } }
}
