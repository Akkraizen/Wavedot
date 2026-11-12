package space.wavedot.core.contract.ui

import kotlinx.coroutines.flow.Flow
import space.wavedot.core.model.Track

data class PluginScreen(
    val id: String,
    val title: String,
    val icon: UiIcon,
    val content: Flow<List<UiNode>>,
)

data class PluginSection(
    val id: String,
    val title: String,
    val content: Flow<List<UiNode>>,
)

data class PluginTrackAction(
    val id: String,
    val label: String,
    val icon: UiIcon,
    val action: UiValueAction<Track>,
    val appliesTo: (Track) -> Boolean = { true },
)

data class PluginAccent(
    val id: String,
    val name: String,
    val argb: Long,
)

data class PluginUi(
    val screens: List<PluginScreen> = emptyList(),
    val librarySections: List<PluginSection> = emptyList(),
    val trackActions: List<PluginTrackAction> = emptyList(),
    val settings: Flow<List<UiNode>>? = null,
    val accents: List<PluginAccent> = emptyList(),
) {
    val isEmpty: Boolean
        get() = screens.isEmpty() && librarySections.isEmpty() && trackActions.isEmpty() && settings == null && accents.isEmpty()
}
