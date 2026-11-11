package space.wavedot.ui.app

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import space.wavedot.core.model.Track
import space.wavedot.presentation.library.LibraryViewModel
import space.wavedot.presentation.library.PlaylistPickerIntent
import space.wavedot.presentation.library.PlaylistPickerViewModel
import space.wavedot.presentation.player.LyricsViewModel
import space.wavedot.presentation.player.PlayerIntent
import space.wavedot.presentation.player.PlayerUiState
import space.wavedot.presentation.player.PlayerViewModel
import space.wavedot.presentation.search.SearchViewModel
import space.wavedot.presentation.settings.AppearanceViewModel
import space.wavedot.presentation.settings.SettingsViewModel
import space.wavedot.ui.designsystem.WavedotIcons
import space.wavedot.ui.designsystem.WavedotTheme
import space.wavedot.ui.designsystem.color.Accents
import space.wavedot.ui.library.LibraryScreen
import space.wavedot.ui.library.PlaylistPickerSheet
import space.wavedot.ui.player.MiniPlayer
import space.wavedot.ui.player.NowPlayingPanel
import space.wavedot.ui.player.NowPlayingScreen
import space.wavedot.ui.player.PlayerBar
import space.wavedot.ui.search.SearchScreen
import space.wavedot.ui.settings.SettingsScreen

enum class Destination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    LIBRARY("Library", WavedotIcons.LibraryMusic, WavedotIcons.LibraryMusicFilled),
    SEARCH("Search", WavedotIcons.Search, WavedotIcons.SearchFilled),
    SETTINGS("Settings", WavedotIcons.Settings, WavedotIcons.SettingsFilled),
}

enum class NavigationLayout { BOTTOM_BAR, SIDE_RAIL }

@Composable
fun WavedotApp(
    httpClient: HttpClient,
    imageCacheDirectory: String?,
    navigationLayout: NavigationLayout = NavigationLayout.BOTTOM_BAR,
    titleBar: @Composable () -> Unit = {},
) {
    InstallImageLoader(httpClient, imageCacheDirectory)
    val appearance = koinViewModel<AppearanceViewModel>()
    val accentId by appearance.accentId.collectAsState()
    WavedotTheme(accent = Accents.byId(accentId)) {
        val playerViewModel = koinViewModel<PlayerViewModel>()
        val playerState by playerViewModel.state.collectAsStateWithLifecycle()
        var destination by rememberSaveable { mutableStateOf(Destination.LIBRARY) }
        var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
        var nowPlayingPanel by rememberSaveable { mutableStateOf(NowPlayingPanel.QUEUE) }
        val lyricsViewModel = koinViewModel<LyricsViewModel>()
        val lyricsState by lyricsViewModel.state.collectAsStateWithLifecycle()
        val pickerViewModel = koinViewModel<PlaylistPickerViewModel>()
        val pickerState by pickerViewModel.state.collectAsStateWithLifecycle()
        val addToPlaylist: (List<Track>) -> Unit = { pickerViewModel.onIntent(PlaylistPickerIntent.Open(it)) }

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
                            onAddToPlaylist = addToPlaylist,
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
                            onAddToPlaylist = addToPlaylist,
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
                            onAddToPlaylist = addToPlaylist,
                        )
                    }
                    PlaylistPickerSheet(pickerState, pickerViewModel::onIntent)
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
    onAddToPlaylist: (List<Track>) -> Unit,
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
            DestinationContent(destination, onAddToPlaylist)
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
    onAddToPlaylist: (List<Track>) -> Unit,
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
                    DestinationContent(target, onAddToPlaylist)
                }
            }
        }
        PlayerBar(
            state = playerState,
            onIntent = onPlayerIntent,
            onExpand = onExpandPlayer,
            onShowLyrics = onShowLyrics,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}

@Composable
private fun DestinationContent(destination: Destination, onAddToPlaylist: (List<Track>) -> Unit) {
    when (destination) {
        Destination.LIBRARY -> {
            val viewModel = koinViewModel<LibraryViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            LibraryScreen(state, viewModel::onIntent, onAddToPlaylist)
        }
        Destination.SEARCH -> {
            val viewModel = koinViewModel<SearchViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SearchScreen(state, viewModel::onIntent, onAddToPlaylist)
        }
        Destination.SETTINGS -> {
            val viewModel = koinViewModel<SettingsViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SettingsScreen(state, viewModel::onIntent)
        }
    }
}
