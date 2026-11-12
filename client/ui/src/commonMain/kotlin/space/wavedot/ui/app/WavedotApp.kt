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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import space.wavedot.presentation.extensions.ExtensionScreen
import space.wavedot.presentation.extensions.ExtensionsIntent
import space.wavedot.presentation.extensions.ExtensionsUiState
import space.wavedot.presentation.extensions.ExtensionsViewModel
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
import space.wavedot.ui.designsystem.LocalExtraTrackActions
import space.wavedot.ui.designsystem.SectionHeader
import space.wavedot.ui.designsystem.TrackAction
import space.wavedot.ui.designsystem.WavedotIcons
import space.wavedot.ui.designsystem.WavedotTheme
import space.wavedot.ui.designsystem.color.AccentColor
import space.wavedot.ui.designsystem.color.Accents
import space.wavedot.ui.extensions.PluginNodes
import space.wavedot.ui.extensions.PluginScreenView
import space.wavedot.ui.extensions.vector
import space.wavedot.ui.library.LibraryScreen
import space.wavedot.ui.library.PlaylistPickerSheet
import space.wavedot.ui.player.MiniPlayer
import space.wavedot.ui.player.NowPlayingPanel
import space.wavedot.ui.player.NowPlayingScreen
import space.wavedot.ui.player.PlayerBar
import space.wavedot.ui.search.SearchScreen
import space.wavedot.ui.settings.SettingsExtras
import space.wavedot.ui.settings.SettingsScreen

data class NavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

enum class NavigationLayout { BOTTOM_BAR, SIDE_RAIL }

private const val LIBRARY = "library"
private const val SEARCH = "search"
private const val SETTINGS = "settings"
private const val PLUGIN_PREFIX = "plugin:"

private fun navItems(screens: List<ExtensionScreen>): List<NavItem> =
    listOf(
        NavItem(LIBRARY, "Library", WavedotIcons.LibraryMusic, WavedotIcons.LibraryMusicFilled),
        NavItem(SEARCH, "Search", WavedotIcons.Search, WavedotIcons.SearchFilled),
    ) + screens.map { NavItem(PLUGIN_PREFIX + it.key, it.title, it.icon.vector, it.icon.vector) } +
        NavItem(SETTINGS, "Settings", WavedotIcons.Settings, WavedotIcons.SettingsFilled)

private class AppChrome(
    val extensions: ExtensionsViewModel,
    val extensionsState: ExtensionsUiState,
    val playingId: MediaId?,
    val onAddToPlaylist: (List<Track>) -> Unit,
    val accents: List<AccentColor>,
)

@Composable
fun WavedotApp(
    httpClient: HttpClient,
    imageCacheDirectory: String?,
    artworkResolver: ArtworkUrlResolver,
    navigationLayout: NavigationLayout = NavigationLayout.BOTTOM_BAR,
    titleBar: @Composable () -> Unit = {},
) {
    InstallImageLoader(httpClient, imageCacheDirectory, artworkResolver)
    val appearance = koinViewModel<AppearanceViewModel>()
    val accentId by appearance.accentId.collectAsState()
    val extensions = koinViewModel<ExtensionsViewModel>()
    val extensionsState by extensions.state.collectAsStateWithLifecycle()
    val pluginAccents = remember(extensionsState.accents) {
        extensionsState.accents.map { Accents.seed(it.id, it.name, it.argb) }
    }
    WavedotTheme(accent = Accents.byId(accentId, pluginAccents)) {
        val playerViewModel = koinViewModel<PlayerViewModel>()
        val playerState by playerViewModel.state.collectAsStateWithLifecycle()
        var destination by rememberSaveable { mutableStateOf(LIBRARY) }
        var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
        var nowPlayingPanel by rememberSaveable { mutableStateOf(NowPlayingPanel.QUEUE) }
        val lyricsViewModel = koinViewModel<LyricsViewModel>()
        val lyricsState by lyricsViewModel.state.collectAsStateWithLifecycle()
        val pickerViewModel = koinViewModel<PlaylistPickerViewModel>()
        val pickerState by pickerViewModel.state.collectAsStateWithLifecycle()
        val addToPlaylist: (List<Track>) -> Unit = { pickerViewModel.onIntent(PlaylistPickerIntent.Open(it)) }
        val items = navItems(extensionsState.screens)
        val current = destination.takeIf { key -> items.any { it.key == key } } ?: LIBRARY
        val chrome = AppChrome(extensions, extensionsState, playerState.track?.id, addToPlaylist, pluginAccents)
        val extraTrackActions = remember(extensionsState) {
            { track: Track ->
                extensions.trackActions(track).map { action ->
                    TrackAction(action.label, action.icon.vector) {
                        extensions.onIntent(ExtensionsIntent.RunTrackAction(action.key, track))
                    }
                }
            }
        }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(extensionsState.message) {
            val message = extensionsState.message ?: return@LaunchedEffect
            snackbar.showSnackbar(message)
            extensions.onIntent(ExtensionsIntent.DismissMessage)
        }

        CompositionLocalProvider(LocalExtraTrackActions provides extraTrackActions) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxSize()) {
                    titleBar()
                    Box(Modifier.weight(1f)) {
                        when (navigationLayout) {
                            NavigationLayout.BOTTOM_BAR -> CompactLayout(
                                items = items,
                                destination = current,
                                onNavigate = { destination = it },
                                playerState = playerState,
                                onPlayerIntent = playerViewModel::onIntent,
                                onExpandPlayer = { nowPlayingOpen = true },
                                chrome = chrome,
                            )
                            NavigationLayout.SIDE_RAIL -> ExpandedLayout(
                                items = items,
                                destination = current,
                                onNavigate = { destination = it },
                                playerState = playerState,
                                onPlayerIntent = playerViewModel::onIntent,
                                onExpandPlayer = { nowPlayingOpen = true },
                                onShowLyrics = {
                                    nowPlayingPanel = NowPlayingPanel.LYRICS
                                    nowPlayingOpen = true
                                },
                                chrome = chrome,
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
                        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
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
    items: List<NavItem>,
    destination: String,
    onNavigate: (String) -> Unit,
    playerState: PlayerUiState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onExpandPlayer: () -> Unit,
    chrome: AppChrome,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Column {
                MiniPlayer(playerState, onPlayerIntent, onExpand = onExpandPlayer)
                ShortNavigationBar {
                    items.forEach { item ->
                        ShortNavigationBarItem(
                            selected = destination == item.key,
                            onClick = { onNavigate(item.key) },
                            icon = { Icon(if (destination == item.key) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            DestinationContent(destination, chrome)
        }
    }
}

@Composable
private fun ExpandedLayout(
    items: List<NavItem>,
    destination: String,
    onNavigate: (String) -> Unit,
    playerState: PlayerUiState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onExpandPlayer: () -> Unit,
    onShowLyrics: () -> Unit,
    chrome: AppChrome,
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
                items.forEach { item ->
                    WideNavigationRailItem(
                        selected = destination == item.key,
                        onClick = { onNavigate(item.key) },
                        icon = { Icon(if (destination == item.key) item.selectedIcon else item.icon, contentDescription = null) },
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
                    DestinationContent(target, chrome)
                }
            }
        }
        PlayerBar(
            state = playerState,
            onIntent = onPlayerIntent,
            onExpand = onExpandPlayer,
            onShowLyrics = onShowLyrics,
            onAddToPlaylist = chrome.onAddToPlaylist,
        )
    }
}

@Composable
private fun DestinationContent(destination: String, chrome: AppChrome) {
    when {
        destination == LIBRARY -> {
            val viewModel = koinViewModel<LibraryViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            LibraryScreen(state, viewModel::onIntent, chrome.onAddToPlaylist, footer = { LibrarySections(chrome) })
        }
        destination == SEARCH -> {
            val viewModel = koinViewModel<SearchViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SearchScreen(state, viewModel::onIntent, chrome.onAddToPlaylist)
        }
        destination == SETTINGS -> {
            val viewModel = koinViewModel<SettingsViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val pages = chrome.extensionsState.settingsPages
            SettingsScreen(
                state = state,
                onIntent = viewModel::onIntent,
                extras = SettingsExtras(
                    accents = chrome.accents,
                    pluginPage = { pluginId ->
                        if (pluginId in pages) {
                            { PluginSettingsPage(pluginId, chrome) }
                        } else {
                            null
                        }
                    },
                ),
            )
        }
        else -> {
            val key = destination.removePrefix(PLUGIN_PREFIX)
            val screen = chrome.extensionsState.screens.firstOrNull { it.key == key } ?: return
            val nodes by chrome.extensions.screenContent(key).collectAsStateWithLifecycle()
            PluginScreenView(screen, nodes, chrome.extensions::onIntent, chrome.playingId, chrome.onAddToPlaylist)
        }
    }
}

@Composable
private fun LibrarySections(chrome: AppChrome) {
    Column {
        chrome.extensionsState.librarySections.forEach { section ->
            val nodes by chrome.extensions.sectionContent(section.key).collectAsStateWithLifecycle()
            SectionHeader(section.title, Modifier.padding(start = 0.dp)) {
                Text(section.pluginName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PluginNodes(nodes, chrome.extensions::onIntent, playingId = chrome.playingId, onAddToPlaylist = chrome.onAddToPlaylist)
        }
    }
}

@Composable
private fun PluginSettingsPage(pluginId: String, chrome: AppChrome) {
    val nodes by chrome.extensions.settingsContent(pluginId).collectAsStateWithLifecycle()
    PluginNodes(nodes, chrome.extensions::onIntent)
}
