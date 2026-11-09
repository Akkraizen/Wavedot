package space.wavedot.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.wavedot.ui.designsystem.PlaylistCover
import space.wavedot.ui.designsystem.SegmentGap
import space.wavedot.ui.designsystem.WavedotIcons
import space.wavedot.ui.designsystem.segmentedItemColors
import space.wavedot.ui.designsystem.trackCount

@Composable
fun PlaylistPickerSheet(state: PlaylistPickerUiState, onIntent: (PlaylistPickerIntent) -> Unit) {
    if (!state.visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onIntent(PlaylistPickerIntent.Dismiss) },
        sheetState = sheetState,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text("Save to", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = subject(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(16.dp))
            NewPlaylistField(onCreate = { onIntent(PlaylistPickerIntent.Create(it)) })
            Spacer(Modifier.size(12.dp))
            val count = state.playlists.size + 1
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(SegmentGap),
            ) {
                item(key = "favorites") {
                    PickerRow(
                        title = LibraryViewModel.FAVORITES_TITLE,
                        subtitle = null,
                        covers = emptyList(),
                        icon = WavedotIcons.FavoriteFilled,
                        membership = state.favorites,
                        index = 0,
                        count = count,
                        onToggle = { onIntent(PlaylistPickerIntent.ToggleFavorites) },
                    )
                }
                itemsIndexed(state.playlists, key = { _, item -> item.summary.id }) { index, item ->
                    PickerRow(
                        title = item.summary.title,
                        subtitle = trackCount(item.summary.trackCount),
                        covers = item.summary.covers,
                        icon = null,
                        membership = item.membership,
                        index = index + 1,
                        count = count,
                        onToggle = { onIntent(PlaylistPickerIntent.TogglePlaylist(item.summary.id)) },
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { onIntent(PlaylistPickerIntent.Dismiss) }, shapes = ButtonDefaults.shapes()) {
                    Text("Done")
                }
            }
        }
    }
}

private fun subject(state: PlaylistPickerUiState): String {
    val single = state.tracks.singleOrNull() ?: return trackCount(state.tracks.size)
    return listOf(single.title, single.artistLine).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun NewPlaylistField(onCreate: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    if (!editing) {
        FilledTonalButton(onClick = { editing = true }, shapes = ButtonDefaults.shapes()) {
            Icon(WavedotIcons.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("New playlist")
        }
        return
    }
    val focus = remember { FocusRequester() }
    val submit = {
        if (name.isNotBlank()) {
            onCreate(name)
            name = ""
            editing = false
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Playlist name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = submit, enabled = name.isNotBlank(), shapes = IconButtonDefaults.shapes()) {
            Icon(WavedotIcons.Check, contentDescription = "Create")
        }
        IconButton(onClick = { editing = false; name = "" }, shapes = IconButtonDefaults.shapes()) {
            Icon(WavedotIcons.Close, contentDescription = "Cancel")
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String?,
    covers: List<String>,
    icon: ImageVector?,
    membership: Membership,
    index: Int,
    count: Int,
    onToggle: () -> Unit,
) {
    SegmentedListItem(
        onClick = onToggle,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = segmentedItemColors(),
        leadingContent = { PlaylistCover(seed = title, size = 48.dp, covers = covers, icon = icon, shape = MaterialTheme.shapes.medium) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            TriStateCheckbox(
                state = when (membership) {
                    Membership.NONE -> ToggleableState.Off
                    Membership.PARTIAL -> ToggleableState.Indeterminate
                    Membership.ALL -> ToggleableState.On
                },
                onClick = onToggle,
            )
        },
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
