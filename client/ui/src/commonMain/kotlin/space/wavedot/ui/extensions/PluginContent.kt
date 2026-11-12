package space.wavedot.ui.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.wavedot.core.contract.ui.UiButtonStyle
import space.wavedot.core.contract.ui.UiCard
import space.wavedot.core.contract.ui.UiEffect
import space.wavedot.core.contract.ui.UiIcon
import space.wavedot.core.contract.ui.UiNode
import space.wavedot.core.contract.ui.UiTextStyle
import space.wavedot.core.contract.ui.UiTone
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import space.wavedot.presentation.extensions.ExtensionsIntent
import space.wavedot.ui.designsystem.Artwork
import space.wavedot.ui.designsystem.SegmentGap
import space.wavedot.ui.designsystem.TrackAction
import space.wavedot.ui.designsystem.TrackListItem
import space.wavedot.ui.designsystem.WavedotIcons

@Composable
fun PluginNodes(
    nodes: List<UiNode>,
    onIntent: (ExtensionsIntent) -> Unit,
    modifier: Modifier = Modifier,
    playingId: MediaId? = null,
    onAddToPlaylist: (List<Track>) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        nodes.forEach { node -> PluginNode(node, onIntent, playingId, onAddToPlaylist) }
    }
}

@Composable
private fun PluginNode(
    node: UiNode,
    onIntent: (ExtensionsIntent) -> Unit,
    playingId: MediaId?,
    onAddToPlaylist: (List<Track>) -> Unit,
) {
    when (node) {
        is UiNode.Heading -> Text(
            text = node.text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
        is UiNode.Text -> Text(
            text = node.text,
            style = when (node.style) {
                UiTextStyle.TITLE -> MaterialTheme.typography.titleLarge
                UiTextStyle.BODY -> MaterialTheme.typography.bodyLarge
                UiTextStyle.CAPTION -> MaterialTheme.typography.bodySmall
            },
            color = if (node.style == UiTextStyle.CAPTION) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        is UiNode.Status -> StatusBanner(node)
        is UiNode.Tracks -> Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
            node.tracks.forEachIndexed { index, track ->
                TrackListItem(
                    track = track,
                    index = index,
                    count = node.tracks.size,
                    favorite = false,
                    playing = track.id == playingId,
                    onClick = { onIntent(ExtensionsIntent.Run { UiEffect.Play(node.tracks, index) }) },
                    onToggleFavorite = null,
                    actions = listOf(TrackAction("Add to playlist", WavedotIcons.PlaylistAdd) { onAddToPlaylist(listOf(track)) }),
                )
            }
        }
        is UiNode.Cards -> LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(node.cards) { _, card -> PluginCard(card) { onIntent(ExtensionsIntent.Run(card.action)) } }
        }
        is UiNode.Button -> PluginButton(node) { onIntent(ExtensionsIntent.Run(node.action)) }
        is UiNode.Toggle -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable { onIntent(ExtensionsIntent.RunWithValue(node.action, !node.checked)) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(node.label, style = MaterialTheme.typography.bodyLarge)
                node.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = node.checked, onCheckedChange = { onIntent(ExtensionsIntent.RunWithValue(node.action, it)) })
        }
        is UiNode.TextField -> PluginTextField(node) { onIntent(ExtensionsIntent.RunWithValue(node.action, it)) }
        is UiNode.Choice -> PluginChoice(node) { onIntent(ExtensionsIntent.RunWithValue(node.action, it)) }
        UiNode.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun StatusBanner(node: UiNode.Status) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (node.tone) {
        UiTone.INFO -> scheme.surfaceContainerHigh to scheme.onSurface
        UiTone.SUCCESS -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        UiTone.WARNING -> scheme.secondaryContainer to scheme.onSecondaryContainer
        UiTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) {
        Text(node.text, color = content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun PluginCard(card: UiCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Artwork(seed = card.title, size = 144.dp, url = card.artworkUrl, shape = MaterialTheme.shapes.large)
        Spacer(Modifier.height(8.dp))
        Text(card.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PluginButton(node: UiNode.Button, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        node.icon?.let {
            Icon(it.vector, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(node.label)
    }
    when (node.style) {
        UiButtonStyle.PRIMARY -> Button(onClick = onClick, shapes = ButtonDefaults.shapes()) { content() }
        UiButtonStyle.TONAL -> FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) { content() }
        UiButtonStyle.TEXT -> TextButton(onClick = onClick, shapes = ButtonDefaults.shapes()) { content() }
    }
}

@Composable
private fun PluginTextField(node: UiNode.TextField, onSubmit: (String) -> Unit) {
    var value by remember(node.label, node.value) { mutableStateOf(node.value) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(node.label) },
            placeholder = node.hint.takeIf { it.isNotEmpty() }?.let { hint -> { Text(hint) } },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            visualTransformation = if (node.secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onSubmit(value) }, enabled = value != node.value, shapes = ButtonDefaults.shapes()) {
            Text(node.submitLabel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginChoice(node: UiNode.Choice, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(node.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            node.options.forEach { option ->
                val selected = option.id == node.selectedId
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onSelect(option.id) },
                    label = { Text(option.label) },
                    leadingIcon = if (selected) {
                        { Icon(WavedotIcons.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
    }
}

val UiIcon.vector: ImageVector
    get() = when (this) {
        UiIcon.MUSIC -> WavedotIcons.MusicNoteFilled
        UiIcon.RADIO -> WavedotIcons.Radio
        UiIcon.ALBUM -> WavedotIcons.AlbumFilled
        UiIcon.ARTIST -> WavedotIcons.PersonFilled
        UiIcon.PLAYLIST -> WavedotIcons.QueueMusic
        UiIcon.FAVORITE -> WavedotIcons.Favorite
        UiIcon.STAR -> WavedotIcons.Star
        UiIcon.TRENDING -> WavedotIcons.TrendingUp
        UiIcon.EXPLORE -> WavedotIcons.Explore
        UiIcon.SEARCH -> WavedotIcons.Search
        UiIcon.PLAY -> WavedotIcons.PlayArrowFilled
        UiIcon.DOWNLOAD -> WavedotIcons.Download
        UiIcon.OPEN -> WavedotIcons.OpenInNew
        UiIcon.SHARE -> WavedotIcons.Share
        UiIcon.SYNC -> WavedotIcons.Sync
        UiIcon.SETTINGS -> WavedotIcons.Settings
        UiIcon.INFO -> WavedotIcons.InfoFilled
        UiIcon.EXTENSION -> WavedotIcons.ExtensionFilled
    }
