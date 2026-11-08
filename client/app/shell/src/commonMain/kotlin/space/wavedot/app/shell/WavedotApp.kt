package space.wavedot.app.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import space.wavedot.feature.library.LibraryScreen
import space.wavedot.feature.library.LibraryViewModel
import space.wavedot.feature.player.MiniPlayer
import space.wavedot.feature.player.LyricsViewModel
import space.wavedot.feature.player.NowPlayingPanel
import space.wavedot.feature.player.NowPlayingScreen
import space.wavedot.feature.player.PlayerBar
import space.wavedot.feature.player.PlayerIntent
import space.wavedot.feature.player.PlayerUiState
import space.wavedot.feature.player.PlayerViewModel
import space.wavedot.feature.search.SearchScreen
import space.wavedot.feature.search.SearchViewModel
import space.wavedot.feature.settings.SettingsScreen
import space.wavedot.feature.settings.SettingsViewModel
import space.wavedot.ui.designsystem.WavedotIcons
import space.wavedot.ui.designsystem.WavedotTheme

enum class Destination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    LIBRARY("Library", WavedotIcons.LibraryMusic, WavedotIcons.LibraryMusicFilled),
    SEARCH("Search", WavedotIcons.Search, WavedotIcons.SearchFilled),
    SETTINGS("Settings", WavedotIcons.Settings, WavedotIcons.SettingsFilled),
}

enum class NavigationLayout { BOTTOM_BAR, SIDE_RAIL }

@Composable
fun WavedotApp(
    navigationLayout: NavigationLayout = NavigationLayout.BOTTOM_BAR,
    titleBar: @Composable () -> Unit = {},
) {
    InstallImageLoader()
    WavedotTheme {
        val playerViewModel = koinViewModel<PlayerViewModel>()
        val playerState by playerViewModel.state.collectAsStateWithLifecycle()
        var destination by rememberSaveable { mutableStateOf(Destination.LIBRARY) }
        var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
        var nowPlayingPanel by rememberSaveable { mutableStateOf(NowPlayingPanel.QUEUE) }
        val lyricsViewModel = koinViewModel<LyricsViewModel>()
        val lyricsState by lyricsViewModel.state.collectAsStateWithLifecycle()

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxSize()) {
                titleBar()
                Box(Modifier.weight(1f)) {
                    when (navigationLayout) {
                        NavigationLayout.BOTTOM_BAR -> CompactLayout(
                            destination = destination,
                            onNavigate = { destination = it },
                            playerState = playerState,
                            onPlayerIntent = playerViewModel::onIntent,
                            onExpandPlayer = { nowPlayingOpen = true },
                        )
                        NavigationLayout.SIDE_RAIL -> ExpandedLayout(
                            destination = destination,
                            onNavigate = { destination = it },
                            playerState = playerState,
                            onPlayerIntent = playerViewModel::onIntent,
                            onExpandPlayer = { nowPlayingOpen = true },
                            onShowLyrics = {
                                nowPlayingPanel = NowPlayingPanel.LYRICS
                                nowPlayingOpen = true
                            },
                        )
                    }
                    NowPlayingOverlay(nowPlayingOpen) {
                        NowPlayingScreen(
                            state = playerState,
                            onIntent = playerViewModel::onIntent,
                            onCollapse = { nowPlayingOpen = false },
                            lyrics = lyricsState,
                            onLyricsIntent = lyricsViewModel::onIntent,
                            panel = nowPlayingPanel,
                            onPanelChange = { nowPlayingPanel = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingOverlay(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        content()
    }
}

@Composable
private fun CompactLayout(
    destination: Destination,
    onNavigate: (Destination) -> Unit,
    playerState: PlayerUiState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onExpandPlayer: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Column {
                MiniPlayer(playerState, onPlayerIntent, onExpand = onExpandPlayer)
                ShortNavigationBar {
                    Destination.entries.forEach { item ->
                        ShortNavigationBarItem(
                            selected = destination == item,
                            onClick = { onNavigate(item) },
                            icon = { Icon(if (destination == item) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            DestinationContent(destination)
        }
    }
}

@Composable
private fun ExpandedLayout(
    destination: Destination,
    onNavigate: (Destination) -> Unit,
    playerState: PlayerUiState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onExpandPlayer: () -> Unit,
    onShowLyrics: () -> Unit,
) {
    val railState = rememberWideNavigationRailState(WideNavigationRailValue.Collapsed)
    val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            WideNavigationRail(
                state = railState,
                colors = WideNavigationRailDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                header = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (railState.targetValue == WideNavigationRailValue.Expanded) railState.collapse() else railState.expand()
                            }
                        },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.padding(start = 24.dp),
                    ) {
                        Icon(
                            imageVector = if (railState.targetValue == WideNavigationRailValue.Expanded) WavedotIcons.MenuOpen else WavedotIcons.Menu,
                            contentDescription = "Toggle navigation",
                        )
                    }
                },
            ) {
                Destination.entries.forEach { item ->
                    WideNavigationRailItem(
                        selected = destination == item,
                        onClick = { onNavigate(item) },
                        icon = { Icon(if (destination == item) item.selectedIcon else item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        railExpanded = railState.targetValue == WideNavigationRailValue.Expanded,
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 12.dp, bottom = 12.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
            ) {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
                ) { target ->
                    DestinationContent(target)
                }
            }
        }
        PlayerBar(playerState, onPlayerIntent, onExpand = onExpandPlayer, onShowLyrics = onShowLyrics)
    }
}

@Composable
private fun DestinationContent(destination: Destination) {
    when (destination) {
        Destination.LIBRARY -> {
            val viewModel = koinViewModel<LibraryViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            LibraryScreen(state, viewModel::onIntent)
        }
        Destination.SEARCH -> {
            val viewModel = koinViewModel<SearchViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SearchScreen(state, viewModel::onIntent)
        }
        Destination.SETTINGS -> {
            val viewModel = koinViewModel<SettingsViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SettingsScreen(state, viewModel::onIntent)
        }
    }
}
