package space.wavedot.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import space.wavedot.feature.library.LibraryScreen
import space.wavedot.feature.library.LibraryViewModel
import space.wavedot.feature.player.MiniPlayer
import space.wavedot.feature.player.NowPlayingScreen
import space.wavedot.feature.player.PlayerIntent
import space.wavedot.feature.player.PlayerUiState
import space.wavedot.feature.player.PlayerViewModel
import space.wavedot.feature.search.SearchScreen
import space.wavedot.feature.search.SearchViewModel
import space.wavedot.feature.settings.SettingsScreen
import space.wavedot.feature.settings.SettingsViewModel
import space.wavedot.ui.designsystem.WavedotTheme

enum class Destination(val label: String, val symbol: String) {
    LIBRARY("Library", "♪"),
    SEARCH("Search", "⌕"),
    SETTINGS("Settings", "⚙"),
}

enum class NavigationLayout { BOTTOM_BAR, SIDE_RAIL }

@Composable
fun WavedotApp(
    navigationLayout: NavigationLayout = NavigationLayout.BOTTOM_BAR,
    titleBar: @Composable () -> Unit = {},
) {
    WavedotTheme {
        val playerViewModel = koinViewModel<PlayerViewModel>()
        val playerState by playerViewModel.state.collectAsStateWithLifecycle()
        var destination by rememberSaveable { mutableStateOf(Destination.LIBRARY) }
        var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                titleBar()
                Box(Modifier.weight(1f)) {
                    when (navigationLayout) {
                        NavigationLayout.BOTTOM_BAR -> BottomBarLayout(
                            destination = destination,
                            onNavigate = { destination = it },
                            playerState = playerState,
                            onPlayerIntent = playerViewModel::onIntent,
                            onExpandPlayer = { nowPlayingOpen = true },
                        )
                        NavigationLayout.SIDE_RAIL -> SideRailLayout(
                            destination = destination,
                            onNavigate = { destination = it },
                            playerState = playerState,
                            onPlayerIntent = playerViewModel::onIntent,
                            onExpandPlayer = { nowPlayingOpen = true },
                        )
                    }
                    if (nowPlayingOpen) {
                        NowPlayingScreen(playerState, playerViewModel::onIntent, onCollapse = { nowPlayingOpen = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarLayout(
    destination: Destination,
    onNavigate: (Destination) -> Unit,
    playerState: PlayerUiState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onExpandPlayer: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(playerState, onPlayerIntent, onExpand = onExpandPlayer)
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { onNavigate(item) },
                            icon = { Text(item.symbol, fontSize = 20.sp) },
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
private fun SideRailLayout(
    destination: Destination,
    onNavigate: (Destination) -> Unit,
    playerState: PlayerUiState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onExpandPlayer: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(Modifier.fillMaxHeight()) {
            Spacer(Modifier.height(8.dp))
            Destination.entries.forEach { item ->
                NavigationRailItem(
                    selected = destination == item,
                    onClick = { onNavigate(item) },
                    icon = { Text(item.symbol, fontSize = 20.sp) },
                    label = { Text(item.label) },
                )
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f)) {
                DestinationContent(destination)
            }
            MiniPlayer(playerState, onPlayerIntent, onExpand = onExpandPlayer)
        }
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
