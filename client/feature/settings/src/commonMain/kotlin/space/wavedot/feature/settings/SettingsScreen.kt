package space.wavedot.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import space.wavedot.ui.designsystem.ScreenHeader
import space.wavedot.ui.designsystem.SectionHeader
import space.wavedot.ui.designsystem.SegmentGap
import space.wavedot.ui.designsystem.WavedotIcons
import space.wavedot.ui.designsystem.WavedotLogo
import space.wavedot.ui.designsystem.segmentedItemColors

@Composable
fun SettingsScreen(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        onIntent(SettingsIntent.DismissMessage)
    }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            ScreenHeader(title = "Settings") {
                if (state.busy) LoadingIndicator(Modifier.size(32.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 840.dp)) {
                SectionHeader("Sources", Modifier.padding(start = 8.dp))
                SegmentedGroup(state.plugins) { plugin, index, count -> PluginItem(plugin, index, count, onIntent) }

                state.musicFolders?.let { folders ->
                    SectionHeader("Music folders", Modifier.padding(start = 8.dp))
                    FoldersGroup(folders, state.busy, onIntent)
                }

                SectionHeader("Sync", Modifier.padding(start = 8.dp))
                SyncGroup(state, onIntent)

                SectionHeader("About", Modifier.padding(start = 8.dp))
                SegmentedListItem(
                    onClick = { onIntent(SettingsIntent.CheckUpdates) },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    colors = segmentedItemColors(),
                    leadingContent = { WavedotLogo(40.dp) },
                    supportingContent = { Text("Version ${state.appVersion}") },
                    trailingContent = {
                        FilledTonalButton(onClick = { onIntent(SettingsIntent.CheckUpdates) }, enabled = !state.busy) {
                            Icon(WavedotIcons.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text("Check for updates", Modifier.padding(start = ButtonDefaults.IconSpacing))
                        }
                    },
                ) {
                    Text("Wavedot")
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun <T> SegmentedGroup(items: List<T>, content: @Composable (T, Int, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        items.forEachIndexed { index, item -> content(item, index, items.size) }
    }
}

@Composable
private fun ShapedIcon(icon: ImageVector, active: Boolean = true) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialShapes.Cookie7Sided.toShape())
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PluginItem(plugin: PluginInfo, index: Int, count: Int, onIntent: (SettingsIntent) -> Unit) {
    var expanded by remember(plugin.id) { mutableStateOf(!plugin.active) }
    Column(
        Modifier
            .clip(ListItemDefaults.segmentedShapes(index = index, count = count).shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        SegmentedListItem(
            onClick = { expanded = !expanded },
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            colors = segmentedItemColors(),
            leadingContent = { ShapedIcon(WavedotIcons.LibraryMusicFilled, plugin.active) },
            supportingContent = { Text(plugin.description.ifBlank { plugin.status }) },
            trailingContent = {
                Text(
                    text = plugin.status,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (plugin.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            Text(plugin.name)
        }
        AnimatedVisibility(expanded && plugin.fields.isNotEmpty()) {
            Column(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plugin.fields.forEach { field ->
                    var value by remember(plugin.id, field.key) { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            label = { Text(field.title) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                onIntent(SettingsIntent.SavePluginValue(plugin.id, field.key, value))
                                value = ""
                            },
                            enabled = value.isNotBlank(),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersGroup(folders: List<String>, busy: Boolean, onIntent: (SettingsIntent) -> Unit) {
    val count = folders.size + 1
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        folders.forEachIndexed { index, folder ->
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
                colors = segmentedItemColors(),
                leadingContent = { ShapedIcon(WavedotIcons.FolderFilled) },
                trailingContent = {
                    IconButton(onClick = { onIntent(SettingsIntent.RemoveFolder(folder)) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(WavedotIcons.Delete, contentDescription = "Remove folder")
                    }
                },
            ) {
                Text(folder)
            }
        }
        var path by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ListItemDefaults.segmentedShapes(index = folders.size, count = count).shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                placeholder = { Text("Add a folder path") },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onIntent(SettingsIntent.AddFolder(path))
                    path = ""
                },
                enabled = path.isNotBlank() && !busy,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(WavedotIcons.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text("Add", Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
    }
}

@Composable
private fun SyncGroup(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        if (state.hubConnected) {
            SegmentedListItem(
                onClick = { onIntent(SettingsIntent.SyncNow) },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                colors = segmentedItemColors(),
                leadingContent = { ShapedIcon(WavedotIcons.Sync) },
                supportingContent = { Text("Favorites are synced through your hub") },
                trailingContent = {
                    Button(onClick = { onIntent(SettingsIntent.SyncNow) }, enabled = !state.busy, shapes = ButtonDefaults.shapes()) {
                        Text("Sync now")
                    }
                },
            ) {
                Text("Connected to Wavedot Hub")
            }
            SegmentedListItem(
                onClick = { onIntent(SettingsIntent.DisconnectHub) },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                colors = segmentedItemColors(),
                leadingContent = { ShapedIcon(WavedotIcons.Close, active = false) },
            ) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
            }
        } else {
            var url by remember { mutableStateOf("") }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ListItemDefaults.segmentedShapes(index = 0, count = 1).shape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShapedIcon(WavedotIcons.Sync, active = false)
                    Column {
                        Text("Not connected", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Connect a hub to sync favorites between devices",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Hub URL") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onIntent(SettingsIntent.ConnectHub(url)) },
                        enabled = url.isNotBlank() && !state.busy,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text("Connect")
                    }
                    FilledTonalButton(
                        onClick = { onIntent(SettingsIntent.SyncNow) },
                        enabled = !state.busy,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text("Import likes")
                    }
                }
            }
        }
    }
}
