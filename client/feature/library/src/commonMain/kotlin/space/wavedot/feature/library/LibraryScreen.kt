package space.wavedot.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.wavedot.ui.designsystem.EmptyState
import space.wavedot.ui.designsystem.ScreenHeader
import space.wavedot.ui.designsystem.SegmentGap
import space.wavedot.ui.designsystem.TrackListItem
import space.wavedot.ui.designsystem.WavedotIcons

@Composable
fun LibraryScreen(state: LibraryUiState, onIntent: (LibraryIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Library",
            subtitle = "${state.favorites.size} favorites · ${state.local.size} on this device",
        ) {
            if (state.tab == LibraryTab.LOCAL) {
                FilledTonalIconButton(
                    onClick = { onIntent(LibraryIntent.Rescan) },
                    enabled = !state.scanning,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    if (state.scanning) LoadingIndicator(Modifier.size(24.dp))
                    else Icon(WavedotIcons.Refresh, contentDescription = "Rescan")
                }
            }
            Button(
                onClick = { onIntent(LibraryIntent.ShuffleAll) },
                enabled = state.visible.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(WavedotIcons.Shuffle, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Shuffle")
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            ToggleButton(
                checked = state.tab == LibraryTab.FAVORITES,
                onCheckedChange = { onIntent(LibraryIntent.SelectTab(LibraryTab.FAVORITES)) },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            ) {
                Icon(WavedotIcons.FavoriteFilled, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Favorites")
            }
            ToggleButton(
                checked = state.tab == LibraryTab.LOCAL,
                onCheckedChange = { onIntent(LibraryIntent.SelectTab(LibraryTab.LOCAL)) },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            ) {
                Icon(WavedotIcons.FolderFilled, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("On this device")
            }
        }
        val tracks = state.visible
        if (tracks.isEmpty()) {
            if (state.tab == LibraryTab.FAVORITES) {
                EmptyState(
                    icon = WavedotIcons.FavoriteFilled,
                    title = "No favorites yet",
                    message = "Tap the heart next to any track to keep it here.",
                )
            } else {
                EmptyState(
                    icon = WavedotIcons.FolderFilled,
                    title = "No local music found",
                    message = "Add music folders in Settings, then rescan.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(SegmentGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id.toString() }) { index, track ->
                    TrackListItem(
                        track = track,
                        index = index,
                        count = tracks.size,
                        favorite = track.id in state.favoriteIds,
                        playing = track.id == state.playingId,
                        onClick = { onIntent(LibraryIntent.Play(index)) },
                        onToggleFavorite = { onIntent(LibraryIntent.ToggleFavorite(track)) },
                    )
                }
            }
        }
    }
}
