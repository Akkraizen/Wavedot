package space.wavedot.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.wavedot.core.domain.search.SearchScope
import space.wavedot.core.domain.search.SourceSearchResult
import space.wavedot.core.model.EntityKind
import space.wavedot.core.model.Track
import space.wavedot.presentation.search.CollectionUi
import space.wavedot.presentation.search.SearchIntent
import space.wavedot.presentation.search.SearchUiState
import space.wavedot.ui.designsystem.AlbumCard
import space.wavedot.ui.designsystem.ArtistCard
import space.wavedot.ui.designsystem.Artwork
import space.wavedot.ui.designsystem.EmptyState
import space.wavedot.ui.designsystem.ScreenHeader
import space.wavedot.ui.designsystem.SectionHeader
import space.wavedot.ui.designsystem.SegmentGap
import space.wavedot.ui.designsystem.TrackAction
import space.wavedot.ui.designsystem.TrackListItem
import space.wavedot.ui.designsystem.WavedotIcons

private const val TRACK_PREVIEW = 5

@Composable
fun SearchScreen(
    state: SearchUiState,
    onIntent: (SearchIntent) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collection = state.collection
    if (collection != null) {
        CollectionScreen(collection, state, onIntent, onAddToPlaylist, modifier)
        return
    }
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = "Search")
        SearchField(state.query, onIntent)
        ScopeSelector(state.scope, onIntent)
        SourceFilter(state, onIntent)
        when {
            state.query.isBlank() -> EmptyState(
                icon = WavedotIcons.SearchFilled,
                title = "Find something to play",
                message = "Tracks, albums and artists from every source appear together.",
            )
            state.loading && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(64.dp))
            }
            state.results.isNotEmpty() && state.results.all { it.isEmpty && it.error == null } -> EmptyState(
                icon = WavedotIcons.Search,
                title = "Nothing found",
                message = "Try another spelling or a different filter.",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                state.results.forEach { result -> sourceSection(result, state, onIntent, onAddToPlaylist) }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onIntent: (SearchIntent) -> Unit) {
    TextField(
        value = query,
        onValueChange = { onIntent(SearchIntent.QueryChanged(it)) },
        placeholder = { Text("Tracks, artists, albums") },
        leadingIcon = { Icon(WavedotIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
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
}

@Composable
private fun ScopeSelector(scope: SearchScope, onIntent: (SearchIntent) -> Unit) {
    val options = listOf(
        SearchScope.ALL to "All",
        SearchScope.TRACKS to "Tracks",
        SearchScope.ALBUMS to "Albums",
        SearchScope.ARTISTS to "Artists",
    )
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (option, label) ->
            ToggleButton(
                checked = scope == option,
                onCheckedChange = { onIntent(SearchIntent.ScopeChanged(option)) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SourceFilter(state: SearchUiState, onIntent: (SearchIntent) -> Unit) {
    if (state.sources.size < 2) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceChip("All sources", selected = state.selectedSources.isEmpty()) { onIntent(SearchIntent.ShowAllSources) }
        state.sources.forEach { source ->
            SourceChip(source.displayName, selected = source.id in state.selectedSources) {
                onIntent(SearchIntent.ToggleSource(source.id))
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(WavedotIcons.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        shape = MaterialTheme.shapes.small,
    )
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.sourceSection(
    result: SourceSearchResult,
    state: SearchUiState,
    onIntent: (SearchIntent) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
) {
    val sourceKey = result.source.id.value
    item(key = "header-$sourceKey") {
        SectionHeader(result.source.displayName, Modifier.padding(start = 0.dp)) {
            if (state.loading) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                val total = result.tracks.items.size + result.albums.items.size + result.artists.items.size
                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) { Text(total.toString()) }
            }
        }
    }
    result.error?.let { error ->
        item(key = "error-$sourceKey") {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
    val artists = result.artists.items
    if (artists.isNotEmpty()) {
        item(key = "artists-$sourceKey") {
            KindLabel("Artists")
            if (state.scope == SearchScope.ARTISTS) {
                FlowRow {
                    artists.forEach { artist -> ArtistCard(artist, onClick = { onIntent(SearchIntent.OpenArtist(artist)) }) }
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 0.dp)) {
                    items(artists, key = { it.id.toString() }) { artist ->
                        ArtistCard(artist, onClick = { onIntent(SearchIntent.OpenArtist(artist)) })
                    }
                }
            }
        }
    }
    val albums = result.albums.items
    if (albums.isNotEmpty()) {
        item(key = "albums-$sourceKey") {
            KindLabel("Albums")
            if (state.scope == SearchScope.ALBUMS) {
                FlowRow {
                    albums.forEach { album -> AlbumCard(album, onClick = { onIntent(SearchIntent.OpenAlbum(album)) }) }
                }
            } else {
                LazyRow {
                    items(albums, key = { it.id.toString() }) { album ->
                        AlbumCard(album, onClick = { onIntent(SearchIntent.OpenAlbum(album)) })
                    }
                }
            }
        }
    }
    val tracks = result.tracks.items
    if (tracks.isNotEmpty()) {
        val shown = if (state.scope == SearchScope.ALL) tracks.take(TRACK_PREVIEW) else tracks
        item(key = "tracks-label-$sourceKey") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindLabel("Tracks", Modifier.weight(1f))
                if (shown.size < tracks.size) {
                    TextButton(onClick = { onIntent(SearchIntent.ScopeChanged(SearchScope.TRACKS)) }) { Text("Show all") }
                }
            }
        }
        itemsIndexed(shown, key = { _, track -> "$sourceKey-track-${track.id}" }) { index, track ->
            TrackListItem(
                track = track,
                index = index,
                count = shown.size,
                favorite = track.id in state.favoriteIds,
                playing = track.id == state.playingId,
                onClick = { onIntent(SearchIntent.Play(tracks, tracks.indexOf(track))) },
                onToggleFavorite = { onIntent(SearchIntent.ToggleFavorite(track)) },
                modifier = Modifier.padding(bottom = SegmentGap),
                actions = trackActions(track, onIntent, onAddToPlaylist),
            )
        }
    }
}

@Composable
private fun KindLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun CollectionScreen(
    collection: CollectionUi,
    state: SearchUiState,
    onIntent: (SearchIntent) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        item(key = "collection-header") {
            Column {
                FilledTonalIconButton(
                    onClick = { onIntent(SearchIntent.CloseCollection) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Icon(WavedotIcons.ArrowBack, contentDescription = "Back")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        seed = collection.title,
                        size = 176.dp,
                        url = collection.artworkUrl,
                        shape = if (collection.kind == EntityKind.ARTIST) CircleShape else MaterialTheme.shapes.extraLarge,
                    )
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (collection.kind == EntityKind.ARTIST) "Artist" else "Album",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = collection.title,
                            style = MaterialTheme.typography.headlineLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        collection.subtitle?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!collection.loading) {
                            Text(
                                text = "${collection.tracks.size} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { onIntent(SearchIntent.PlayCollection(shuffle = false)) },
                                enabled = collection.tracks.isNotEmpty(),
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Icon(WavedotIcons.PlayArrowFilled, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Text("Play", Modifier.padding(start = ButtonDefaults.IconSpacing))
                            }
                            FilledTonalButton(
                                onClick = { onIntent(SearchIntent.PlayCollection(shuffle = true)) },
                                enabled = collection.tracks.isNotEmpty(),
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Icon(WavedotIcons.Shuffle, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Text("Shuffle", Modifier.padding(start = ButtonDefaults.IconSpacing))
                            }
                            IconButton(
                                onClick = { onAddToPlaylist(collection.tracks) },
                                enabled = collection.tracks.isNotEmpty(),
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(WavedotIcons.PlaylistAdd, contentDescription = "Add all to playlist")
                            }
                        }
                    }
                }
            }
        }
        when {
            collection.loading -> item(key = "collection-loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(64.dp))
                }
            }
            collection.error != null -> item(key = "collection-error") {
                Text(collection.error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            else -> itemsIndexed(collection.tracks, key = { index, track -> "collection-$index-${track.id}" }) { index, track ->
                TrackListItem(
                    track = track,
                    index = index,
                    count = collection.tracks.size,
                    favorite = track.id in state.favoriteIds,
                    playing = track.id == state.playingId,
                    onClick = { onIntent(SearchIntent.Play(collection.tracks, index)) },
                    onToggleFavorite = { onIntent(SearchIntent.ToggleFavorite(track)) },
                    actions = trackActions(track, onIntent, onAddToPlaylist),
                )
            }
        }
    }
}

private fun trackActions(
    track: Track,
    onIntent: (SearchIntent) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
): List<TrackAction> = listOf(
    TrackAction("Add to playlist", WavedotIcons.PlaylistAdd) { onAddToPlaylist(listOf(track)) },
    TrackAction("Play next", WavedotIcons.QueueMusic) { onIntent(SearchIntent.PlayNext(track)) },
)
