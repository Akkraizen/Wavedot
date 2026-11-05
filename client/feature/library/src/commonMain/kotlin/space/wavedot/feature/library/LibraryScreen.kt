package space.wavedot.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.wavedot.ui.designsystem.EmptyState
import space.wavedot.ui.designsystem.SectionHeader
import space.wavedot.ui.designsystem.TrackRow

@Composable
fun LibraryScreen(state: LibraryUiState, onIntent: (LibraryIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        SectionHeader("Library") {
            Row {
                TextButton(onClick = { onIntent(LibraryIntent.ShuffleAll) }) { Text("Shuffle") }
                if (state.tab == LibraryTab.LOCAL) {
                    TextButton(onClick = { onIntent(LibraryIntent.Rescan) }, enabled = !state.scanning) {
                        Text(if (state.scanning) "Scanning…" else "Rescan")
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp)) {
            FilterChip(
                selected = state.tab == LibraryTab.FAVORITES,
                onClick = { onIntent(LibraryIntent.SelectTab(LibraryTab.FAVORITES)) },
                label = { Text("Favorites · ${state.favorites.size}") },
                modifier = Modifier.padding(end = 8.dp),
            )
            FilterChip(
                selected = state.tab == LibraryTab.LOCAL,
                onClick = { onIntent(LibraryIntent.SelectTab(LibraryTab.LOCAL)) },
                label = { Text("On this device · ${state.local.size}") },
            )
        }
        val tracks = state.visible
        if (tracks.isEmpty()) {
            EmptyState(
                title = if (state.tab == LibraryTab.FAVORITES) "No favorites yet" else "No local music found",
                message = if (state.tab == LibraryTab.FAVORITES) "Tap ♡ next to a track to keep it here."
                else "Add music folders in Settings, then rescan.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tracks, key = { _, track -> track.id.toString() }) { index, track ->
                    TrackRow(
                        track = track,
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
