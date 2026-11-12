package space.wavedot.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import space.wavedot.presentation.settings.PluginInfo
import space.wavedot.presentation.settings.SettingsIntent
import space.wavedot.presentation.settings.SettingsSection
import space.wavedot.presentation.settings.SettingsUiState
import space.wavedot.ui.designsystem.ScreenHeader
import space.wavedot.ui.designsystem.SectionHeader
import space.wavedot.ui.designsystem.SegmentGap
import space.wavedot.ui.designsystem.WavedotIcons
import space.wavedot.ui.designsystem.WavedotLogo
import space.wavedot.ui.designsystem.color.AccentColor
import space.wavedot.ui.designsystem.color.AccentKind
import space.wavedot.ui.designsystem.color.Accents
import space.wavedot.ui.designsystem.color.isSystemAccentSupported
import space.wavedot.ui.designsystem.color.systemColorScheme
import space.wavedot.ui.designsystem.segmentedItemColors

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    extras: SettingsExtras = SettingsExtras(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        onIntent(SettingsIntent.DismissMessage)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= TWO_PANE_WIDTH
        val opened = state.section
        when {
            wide -> Row(Modifier.fillMaxSize()) {
                val selected = opened ?: state.sections.first()
                SectionList(state, selected, onIntent, extras, Modifier.width(340.dp).fillMaxHeight())
                SectionPane(selected, state, onIntent, extras, showBack = false, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            opened == null -> SectionList(state, null, onIntent, extras, Modifier.fillMaxSize())
            else -> SectionPane(opened, state, onIntent, extras, showBack = true, modifier = Modifier.fillMaxSize())
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

private val TWO_PANE_WIDTH = 760.dp

@Composable
private fun SectionList(
    state: SettingsUiState,
    selected: SettingsSection?,
    onIntent: (SettingsIntent) -> Unit,
    extras: SettingsExtras,
    modifier: Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        ScreenHeader(title = "Settings") {
            if (state.busy && selected == null) LoadingIndicator(Modifier.size(32.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
            state.sections.forEachIndexed { index, section ->
                val isSelected = section == selected
                SegmentedListItem(
                    onClick = { onIntent(SettingsIntent.OpenSection(section)) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = state.sections.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    leadingContent = { ShapedIcon(section.icon) },
                    supportingContent = { Text(section.summary(state, extras)) },
                    trailingContent = if (selected == null) {
                        { Icon(WavedotIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        null
                    },
                ) {
                    Text(section.title)
                }
            }
        }
    }
}

private val SettingsSection.title: String
    get() = when (this) {
        SettingsSection.APPEARANCE -> "Appearance"
        SettingsSection.PLUGINS -> "Plugins"
        SettingsSection.LIBRARY -> "Library"
        SettingsSection.SYNC -> "Sync"
        SettingsSection.ABOUT -> "About"
    }

private val SettingsSection.icon: ImageVector
    get() = when (this) {
        SettingsSection.APPEARANCE -> WavedotIcons.PaletteFilled
        SettingsSection.PLUGINS -> WavedotIcons.ExtensionFilled
        SettingsSection.LIBRARY -> WavedotIcons.FolderFilled
        SettingsSection.SYNC -> WavedotIcons.Sync
        SettingsSection.ABOUT -> WavedotIcons.InfoFilled
    }

private fun SettingsSection.summary(state: SettingsUiState, extras: SettingsExtras): String = when (this) {
    SettingsSection.APPEARANCE -> "Accent color: ${Accents.byId(state.accentId, extras.accents).name}"
    SettingsSection.PLUGINS -> {
        val active = state.plugins.count { it.active }
        "$active of ${state.plugins.size} active"
    }
    SettingsSection.LIBRARY -> when (val count = state.musicFolders.orEmpty().size) {
        0 -> "No music folders"
        1 -> "1 music folder"
        else -> "$count music folders"
    }
    SettingsSection.SYNC -> if (state.hubConnected) "Connected to a hub" else "Not connected"
    SettingsSection.ABOUT -> "Version ${state.appVersion} · updates"
}

@Composable
private fun SectionPane(
    section: SettingsSection,
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    extras: SettingsExtras,
    showBack: Boolean,
    modifier: Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                FilledTonalIconButton(onClick = { onIntent(SettingsIntent.CloseSection) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(WavedotIcons.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(section.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (state.busy) LoadingIndicator(Modifier.size(32.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 760.dp)) {
            when (section) {
                SettingsSection.APPEARANCE -> {
                    Spacer(Modifier.height(12.dp))
                    AccentPicker(Accents.available(isSystemAccentSupported()) + extras.accents, Accents.byId(state.accentId, extras.accents).id) { onIntent(SettingsIntent.SelectAccent(it)) }
                }
                SettingsSection.PLUGINS -> PluginsSection(state, onIntent, extras)
                SettingsSection.LIBRARY -> {
                    SectionHeader("Music folders", Modifier.padding(start = 8.dp))
                    FoldersGroup(state.musicFolders.orEmpty(), state.busy, onIntent)
                }
                SettingsSection.SYNC -> {
                    Spacer(Modifier.height(12.dp))
                    SyncGroup(state, onIntent)
                }
                SettingsSection.ABOUT -> {
                    Spacer(Modifier.height(12.dp))
                    AboutItem(state, onIntent)
                }
            }
        }
    }
}

@Composable
private fun PluginsSection(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit, extras: SettingsExtras) {
    Text(
        text = "Plugins add music sources and lyrics. Expand a plugin to set it up.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
    )
    if (state.sourcePlugins.isNotEmpty()) {
        SectionHeader("Sources", Modifier.padding(start = 8.dp))
        SegmentedGroup(state.sourcePlugins) { plugin, index, count -> PluginItem(plugin, index, count, onIntent, extras.pluginPage(plugin.id)) }
    }
    if (state.lyricsPlugins.isNotEmpty()) {
        SectionHeader("Lyrics", Modifier.padding(start = 8.dp))
        SegmentedGroup(state.lyricsPlugins) { plugin, index, count -> PluginItem(plugin, index, count, onIntent, extras.pluginPage(plugin.id)) }
    }
}

@Composable
private fun AboutItem(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(accents: List<AccentColor>, selectedId: String, onSelect: (String) -> Unit) {
    val dark = isSystemInDarkTheme()
    val systemPrimary = systemColorScheme(dark)?.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemDefaults.segmentedShapes(index = 0, count = 1).shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
    ) {
        Text("Accent color", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Tints buttons, highlights, surfaces and the player",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            accents.forEach { accent ->
                val color = if (accent.kind == AccentKind.System) systemPrimary ?: accent.swatch else accent.swatch
                AccentSwatch(accent.name, color, selected = accent.id == selectedId) { onSelect(accent.id) }
            }
        }
    }
}

@Composable
private fun AccentSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val size by animateDpAsState(if (selected) 56.dp else 48.dp, MaterialTheme.motionScheme.fastSpatialSpec())
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = WavedotIcons.Check,
                        contentDescription = null,
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    )
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
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
private fun PluginItem(
    plugin: PluginInfo,
    index: Int,
    count: Int,
    onIntent: (SettingsIntent) -> Unit,
    page: (@Composable () -> Unit)?,
) {
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
        if (page != null) {
            AnimatedVisibility(expanded) {
                Box(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 16.dp)) { page() }
            }
        }
        AnimatedVisibility(page == null && expanded && plugin.fields.isNotEmpty()) {
            Column(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plugin.fields.forEach { field ->
                    var value by remember(plugin.id, field.key, field.value) { mutableStateOf(field.value.orEmpty()) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            label = { Text(field.title) },
                            placeholder = field.hint.takeIf { it.isNotEmpty() }?.let { hint -> { Text(hint) } },
                            supportingText = if (field.secret && field.configured) {
                                { Text("Saved. Enter a new value to replace it.") }
                            } else {
                                null
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                onIntent(SettingsIntent.SavePluginValue(plugin.id, field.key, value))
                                if (field.secret) value = ""
                            },
                            enabled = value.isNotBlank() && value != field.value,
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
private fun DeleteHubAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(WavedotIcons.Delete, contentDescription = null) },
        title = { Text("Delete hub account?") },
        text = { Text("The account, every device signed in to it and all synced data are removed from the hub. Favorites on this device stay.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SyncGroup(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        if (state.hubConnected) {
            var confirmAccountDeletion by remember { mutableStateOf(false) }
            if (confirmAccountDeletion) {
                DeleteHubAccountDialog(
                    onConfirm = {
                        confirmAccountDeletion = false
                        onIntent(SettingsIntent.DeleteHubAccount)
                    },
                    onDismiss = { confirmAccountDeletion = false },
                )
            }
            SegmentedListItem(
                onClick = { onIntent(SettingsIntent.SyncNow) },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
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
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                colors = segmentedItemColors(),
                leadingContent = { ShapedIcon(WavedotIcons.Close, active = false) },
                supportingContent = { Text("Signs this device out; synced data stays on the hub") },
            ) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
            }
            SegmentedListItem(
                onClick = { confirmAccountDeletion = true },
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                colors = segmentedItemColors(),
                leadingContent = { ShapedIcon(WavedotIcons.Close, active = false) },
                supportingContent = { Text("Removes the account, its devices and everything synced") },
            ) {
                Text("Delete hub account", color = MaterialTheme.colorScheme.error)
            }
        } else {
            var url by remember { mutableStateOf("") }
            var registrationToken by remember { mutableStateOf("") }
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
                    placeholder = { Text("https://hub.example.com") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = registrationToken,
                    onValueChange = { registrationToken = it },
                    label = { Text("Registration token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onIntent(SettingsIntent.ConnectHub(url, registrationToken)) },
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
