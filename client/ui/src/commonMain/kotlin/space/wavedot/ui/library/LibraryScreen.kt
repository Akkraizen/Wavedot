package space.wavedot.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.wavedot.core.model.Track
import space.wavedot.presentation.library.CollectionDetails
import space.wavedot.presentation.library.LibraryCollection
import space.wavedot.presentation.library.LibraryIntent
import space.wavedot.presentation.library.LibraryUiState
import space.wavedot.ui.designsystem.EmptyState
import space.wavedot.ui.designsystem.PlaylistCard
import space.wavedot.ui.designsystem.PlaylistCover
import space.wavedot.ui.designsystem.ScreenHeader
import space.wavedot.ui.designsystem.SegmentGap
import space.wavedot.ui.designsystem.TrackAction
import space.wavedot.ui.designsystem.TrackListItem
import space.wavedot.ui.designsystem.WavedotIcons
import space.wavedot.ui.designsystem.toLength
import space.wavedot.ui.designsystem.trackCount

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onIntent: (LibraryIntent) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    val opened = state.opened
    if (opened != null) {
        CollectionScreen(opened, state, onIntent, onAddToPlaylist, modifier)
        return
    }
    var creating by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Library",
            subtitle = when (state.playlistCount) {
                0 -> "Favorites and music on this device"
                1 -> "1 playlist"
                else -> "${state.playlistCount} playlists"
            },
        ) {
            Button(onClick = { creating = true }, shapes = ButtonDefaults.shapes()) {
                Icon(WavedotIcons.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("New playlist")
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        ) {
            items(state.shelves, key = { it.collection.key }) { shelf ->
                PlaylistCard(
                    title = shelf.title,
                    subtitle = trackCount(shelf.trackCount),
                    covers = shelf.covers,
                    icon = shelf.collection.icon,
                    onClick = { onIntent(LibraryIntent.Open(shelf.collection)) },
                )
            }
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    }
    if (creating) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = {
                creating = false
                onIntent(LibraryIntent.CreatePlaylist(it))
            },
            onDismiss = { creating = false },
        )
    }
}

private val LibraryCollection.key: String
    get() = when (this) {
        LibraryCollection.Favorites -> "favorites"
        LibraryCollection.Local -> "local"
        is LibraryCollection.Playlist -> "playlist-$id"
    }

private val LibraryCollection.icon: ImageVector?
    get() = when (this) {
        LibraryCollection.Favorites -> WavedotIcons.FavoriteFilled
        LibraryCollection.Local -> WavedotIcons.FolderFilled
        is LibraryCollection.Playlist -> null
    }

@Composable
private fun CollectionScreen(
    details: CollectionDetails,
    state: LibraryUiState,
    onIntent: (LibraryIntent) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    modifier: Modifier,
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        item(key = "header") {
            CollectionHeader(
                details = details,
                scanning = state.scanning,
                onIntent = onIntent,
                onAddToPlaylist = { onAddToPlaylist(details.tracks) },
                onRename = { renaming = true },
                onDelete = { deleting = true },
            )
        }
        if (details.tracks.isEmpty()) {
            item(key = "empty") { CollectionEmptyState(details.collection) }
        } else {
            itemsIndexed(details.tracks, key = { _, track -> track.id.toString() }) { index, track ->
                TrackListItem(
                    track = track,
                    index = index,
                    count = details.tracks.size,
                    favorite = track.id in state.favoriteIds,
                    playing = track.id == state.playingId,
                    onClick = { onIntent(LibraryIntent.Play(index)) },
                    onToggleFavorite = { onIntent(LibraryIntent.ToggleFavorite(track)) },
                    actions = trackActions(details, track, onIntent, onAddToPlaylist),
                )
            }
        }
    }
    if (renaming) {
        PlaylistNameDialog(
            title = "Rename playlist",
            confirmLabel = "Save",
            initial = details.title,
            onConfirm = {
                renaming = false
                onIntent(LibraryIntent.RenamePlaylist(it))
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        DeletePlaylistDialog(
            title = details.title,
            onConfirm = {
                deleting = false
                onIntent(LibraryIntent.DeletePlaylist)
            },
            onDismiss = { deleting = false },
        )
    }
}

private fun trackActions(
    details: CollectionDetails,
    track: Track,
    onIntent: (LibraryIntent) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
): List<TrackAction> = buildList {
    add(TrackAction("Add to playlist", WavedotIcons.PlaylistAdd) { onAddToPlaylist(listOf(track)) })
    add(TrackAction("Play next", WavedotIcons.QueueMusic) { onIntent(LibraryIntent.PlayNext(track)) })
    if (details.editable) {
        add(TrackAction("Remove from playlist", WavedotIcons.Delete) { onIntent(LibraryIntent.RemoveFromCollection(track)) })
    }
}

@Composable
private fun CollectionHeader(
    details: CollectionDetails,
    scanning: Boolean,
    onIntent: (LibraryIntent) -> Unit,
    onAddToPlaylist: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        FilledTonalIconButton(
            onClick = { onIntent(LibraryIntent.Close) },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Icon(WavedotIcons.ArrowBack, contentDescription = "Back")
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaylistCover(
                seed = details.title,
                size = 176.dp,
                covers = details.covers,
                icon = details.collection.icon,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (details.editable) "Playlist" else "Collection",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = details.title,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        trackCount(details.tracks.size),
                        details.duration.takeIf { it.isPositive() }?.toLength(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { onIntent(LibraryIntent.PlayAll(shuffle = false)) },
                        enabled = details.tracks.isNotEmpty(),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(WavedotIcons.PlayArrowFilled, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text("Play", Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    FilledTonalButton(
                        onClick = { onIntent(LibraryIntent.PlayAll(shuffle = true)) },
                        enabled = details.tracks.isNotEmpty(),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(WavedotIcons.Shuffle, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text("Shuffle", Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    Row {
                        HeaderAction(WavedotIcons.PlaylistAdd, "Add all to playlist", enabled = details.tracks.isNotEmpty(), onClick = onAddToPlaylist)
                        if (details.editable) {
                            HeaderAction(WavedotIcons.Edit, "Rename", onClick = onRename)
                            HeaderAction(WavedotIcons.Delete, "Delete playlist", onClick = onDelete)
                        }
                        if (details.collection == LibraryCollection.Local) {
                            if (scanning) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(24.dp)) }
                            } else {
                                HeaderAction(WavedotIcons.Refresh, "Rescan", onClick = { onIntent(LibraryIntent.Rescan) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun CollectionEmptyState(collection: LibraryCollection) {
    val (icon, title, message) = when (collection) {
        LibraryCollection.Favorites -> Triple(
            WavedotIcons.FavoriteFilled,
            "No favorites yet",
            "Tap the heart next to any track to keep it here.",
        )
        LibraryCollection.Local -> Triple(
            WavedotIcons.FolderFilled,
            "No local music found",
            "Add music folders in Settings, then rescan.",
        )
        is LibraryCollection.Playlist -> Triple(
            WavedotIcons.PlaylistAdd,
            "This playlist is empty",
            "Use \"Add to playlist\" in a track's menu or in the player to fill it.",
        )
    }
    EmptyState(icon = icon, title = title, message = message, modifier = Modifier.height(360.dp))
}
