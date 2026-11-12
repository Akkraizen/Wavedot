package space.wavedot.ui.extensions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.wavedot.core.contract.ui.UiNode
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import space.wavedot.presentation.extensions.ExtensionScreen
import space.wavedot.presentation.extensions.ExtensionsIntent
import space.wavedot.ui.designsystem.ScreenHeader

@Composable
fun PluginScreenView(
    screen: ExtensionScreen,
    nodes: List<UiNode>,
    onIntent: (ExtensionsIntent) -> Unit,
    playingId: MediaId?,
    onAddToPlaylist: (List<Track>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader(title = screen.title, subtitle = screen.pluginName)
        PluginNodes(
            nodes = nodes,
            onIntent = onIntent,
            playingId = playingId,
            onAddToPlaylist = onAddToPlaylist,
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 960.dp),
        )
    }
}
