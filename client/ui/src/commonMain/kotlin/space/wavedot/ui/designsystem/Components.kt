package space.wavedot.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import okio.Path.Companion.toPath
import space.wavedot.core.model.Album
import space.wavedot.core.model.Artist
import space.wavedot.core.model.Track

@Composable
fun Artwork(
    seed: String,
    size: Dp,
    url: String? = null,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    spinning: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val hue = ((seed.hashCode().toUInt() * 2654435761u) % 360u).toFloat()
    val accent = Color.hsv(hue, 0.45f, 0.9f)
    val brush = Brush.linearGradient(
        listOf(lerp(scheme.primaryContainer, accent, 0.7f), lerp(scheme.tertiaryContainer, accent, 0.45f)),
    )
    val rotation = if (spinning) {
        val transition = rememberInfiniteTransition()
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Restart),
        )
        angle
    } else {
        0f
    }
    Box(
        modifier = modifier.size(size).rotate(rotation).clip(shape).background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = WavedotIcons.MusicNoteFilled,
            contentDescription = null,
            tint = scheme.onPrimaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(size * 0.38f).rotate(-rotation),
        )
        if (url != null) {
            AsyncImage(
                model = artworkModel(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

fun artworkModel(url: String): Any =
    if (url.contains("://")) url else url.replace('\\', '/').toPath()

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Artwork(
            seed = album.title,
            size = width - 16.dp,
            url = album.artwork?.url,
            shape = MaterialTheme.shapes.large,
        )
        Spacer(Modifier.height(8.dp))
        Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = listOfNotNull(album.artists.firstOrNull()?.name, album.year?.toString()).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 128.dp,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            seed = artist.name,
            size = width - 16.dp,
            url = artist.artwork?.url,
            shape = CircleShape,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun TrackListItem(
    track: Track,
    index: Int,
    count: Int,
    favorite: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: List<TrackAction> = emptyList(),
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier,
        colors = segmentedItemColors(),
        leadingContent = {
            Artwork(
                seed = track.album?.title ?: track.title,
                size = 48.dp,
                url = track.artwork?.url,
                shape = if (playing) MaterialShapes.Cookie9Sided.toShape() else MaterialTheme.shapes.medium,
                spinning = playing,
            )
        },
        supportingContent = {
            Text(
                text = track.artistLine.ifEmpty { track.id.source.value },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                track.duration?.let {
                    Text(it.toClock(), style = MaterialTheme.typography.labelMedium)
                }
                onToggleFavorite?.let { FavoriteToggle(favorite, it) }
                val allActions = actions + LocalExtraTrackActions.current(track)
                if (allActions.isNotEmpty()) TrackActionsMenu(allActions)
            }
        },
    ) {
        Text(
            text = track.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

val LocalExtraTrackActions = compositionLocalOf<(Track) -> List<TrackAction>> { { emptyList() } }

data class TrackAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun TrackActionsMenu(actions: List<TrackAction>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(WavedotIcons.MoreVert, contentDescription = "More actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
fun PlaylistCover(
    seed: String,
    size: Dp,
    covers: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = MaterialTheme.shapes.large,
) {
    when {
        icon != null -> Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.4f))
        }
        covers.size >= MOSAIC_SIZE -> Column(modifier.size(size).clip(shape)) {
            covers.take(MOSAIC_SIZE).chunked(2).forEach { row ->
                Row(Modifier.weight(1f)) {
                    row.forEach { url ->
                        AsyncImage(
                            model = artworkModel(url),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        else -> Artwork(seed = seed, size = size, url = covers.firstOrNull(), modifier = modifier, shape = shape)
    }
}

private const val MOSAIC_SIZE = 4

@Composable
fun PlaylistCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    covers: List<String> = emptyList(),
    icon: ImageVector? = null,
) {
    BoxWithConstraints(modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick).padding(8.dp)) {
        val coverSize = maxWidth
        Column {
            PlaylistCover(seed = title, size = coverSize, covers = covers, icon = icon)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FavoriteToggle(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconToggleButton(
        checked = favorite,
        onCheckedChange = { onToggle() },
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (favorite) WavedotIcons.FavoriteFilled else WavedotIcons.Favorite,
            contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.let {
            Spacer(Modifier.height(24.dp))
            it()
        }
    }
}

val SegmentGap: Dp = 2.dp

@Composable
fun segmentedItemColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
