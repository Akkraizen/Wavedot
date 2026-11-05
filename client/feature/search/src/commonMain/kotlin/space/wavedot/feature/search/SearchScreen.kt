package space.wavedot.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.wavedot.ui.designsystem.EmptyState
import space.wavedot.ui.designsystem.SectionHeader
import space.wavedot.ui.designsystem.TrackRow

@Composable
fun SearchScreen(state: SearchUiState, onIntent: (SearchIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onIntent(SearchIntent.QueryChanged(it)) },
            placeholder = { Text("Search tracks in every source") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.query.isBlank()) {
            EmptyState("Search", "Results from local files and connected services appear together.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            state.results.forEach { result ->
                item(key = "header-${result.source.id}") { SectionHeader(result.source.displayName) }
                result.error?.let { error ->
                    item(key = "error-${result.source.id}") {
                        Text(
                            error.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                if (result.error == null && result.page.items.isEmpty()) {
                    item(key = "empty-${result.source.id}") {
                        Text(
                            "Nothing found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                itemsIndexed(result.page.items, key = { _, track -> "${result.source.id}-${track.id}" }) { index, track ->
                    TrackRow(
                        track = track,
                        favorite = track.id in state.favoriteIds,
                        playing = track.id == state.playingId,
                        onClick = { onIntent(SearchIntent.Play(result, index)) },
                        onToggleFavorite = { onIntent(SearchIntent.ToggleFavorite(track)) },
                    )
                }
            }
        }
    }
}
