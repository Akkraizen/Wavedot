package space.wavedot.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import space.wavedot.ui.designsystem.EmptyState
import space.wavedot.ui.designsystem.ScreenHeader
import space.wavedot.ui.designsystem.SectionHeader
import space.wavedot.ui.designsystem.TrackListItem
import space.wavedot.ui.designsystem.WavedotIcons

@Composable
fun SearchScreen(state: SearchUiState, onIntent: (SearchIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = "Search")
        TextField(
            value = state.query,
            onValueChange = { onIntent(SearchIntent.QueryChanged(it)) },
            placeholder = { Text("Tracks, artists, albums") },
            leadingIcon = { Icon(WavedotIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onIntent(SearchIntent.QueryChanged("")) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(WavedotIcons.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
        )
        when {
            state.query.isBlank() -> EmptyState(
                icon = WavedotIcons.SearchFilled,
                title = "Find something to play",
                message = "Results from local files and connected services appear together.",
            )
            state.loading && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(64.dp))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.results.forEach { result ->
                    item(key = "header-${result.source.id}") {
                        SectionHeader(result.source.displayName, Modifier.padding(start = 0.dp)) {
                            if (state.loading) LoadingIndicator(Modifier.size(24.dp))
                            else Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(result.page.items.size.toString())
                            }
                        }
                    }
                    result.error?.let { error ->
                        item(key = "error-${result.source.id}") {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = error.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                    if (result.error == null && result.page.items.isEmpty()) {
                        item(key = "empty-${result.source.id}") {
                            Text(
                                text = "Nothing found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                        }
                    }
                    itemsIndexed(result.page.items, key = { _, track -> "${result.source.id}-${track.id}" }) { index, track ->
                        TrackListItem(
                            track = track,
                            index = index,
                            count = result.page.items.size,
                            favorite = track.id in state.favoriteIds,
                            playing = track.id == state.playingId,
                            onClick = { onIntent(SearchIntent.Play(result, index)) },
                            onToggleFavorite = { onIntent(SearchIntent.ToggleFavorite(track)) },
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
