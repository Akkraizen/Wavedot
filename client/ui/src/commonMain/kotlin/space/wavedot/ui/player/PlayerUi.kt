package space.wavedot.ui.player

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.wavedot.core.domain.player.RepeatMode
import space.wavedot.core.model.Track
import space.wavedot.presentation.player.LyricsIntent
import space.wavedot.presentation.player.LyricsUiState
import space.wavedot.presentation.player.PlayerIntent
import space.wavedot.presentation.player.PlayerUiState
import space.wavedot.ui.designsystem.Artwork
import space.wavedot.ui.designsystem.FavoriteToggle
import space.wavedot.ui.designsystem.TrackAction
import space.wavedot.ui.designsystem.TrackListItem
import space.wavedot.ui.designsystem.WavedotIcons
import space.wavedot.ui.designsystem.toClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MiniPlayer(state: PlayerUiState, onIntent: (PlayerIntent) -> Unit, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val track = state.track ?: return
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    seed = track.album?.title ?: track.title,
            url = track.artwork?.url,
                    size = 48.dp,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    spinning = state.isPlaying,
                )
                Spacer(Modifier.width(12.dp))
                TrackTitles(state, Modifier.weight(1f))
                PlayPauseButton(state, onIntent, large = false)
                IconButton(onClick = { onIntent(PlayerIntent.Next) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(WavedotIcons.SkipNextFilled, contentDescription = "Next")
                }
            }
            PlaybackWave(state, Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp))
        }
    }
}

@Composable
fun PlayerBar(
    state: PlayerUiState,
    onIntent: (PlayerIntent) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onShowLyrics: () -> Unit = onExpand,
    onAddToPlaylist: (List<Track>) -> Unit = {},
) {
    val track = state.track ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.large).clickable(onClick = onExpand).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    seed = track.album?.title ?: track.title,
            url = track.artwork?.url,
                    size = 64.dp,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    spinning = state.isPlaying,
                )
                Spacer(Modifier.width(14.dp))
                TrackTitles(state, Modifier.weight(1f, fill = false))
                FavoriteToggle(state.favorite, { onIntent(PlayerIntent.ToggleFavorite) })
                AddToPlaylistButton { onAddToPlaylist(listOf(track)) }
            }
            Column(
                modifier = Modifier.weight(1.4f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TransportControls(state, onIntent, large = false)
                SeekRow(state, onIntent, Modifier.widthIn(max = 560.dp))
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VolumeControl(state, onIntent, Modifier.width(160.dp))
                IconButton(onClick = onShowLyrics, shapes = IconButtonDefaults.shapes()) {
                    Icon(WavedotIcons.Lyrics, contentDescription = "Lyrics")
                }
                IconButton(onClick = onExpand, shapes = IconButtonDefaults.shapes()) {
                    Icon(WavedotIcons.QueueMusic, contentDescription = "Queue")
                }
            }
        }
    }
}

enum class NowPlayingPanel { QUEUE, LYRICS }

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    onIntent: (PlayerIntent) -> Unit,
    onCollapse: () -> Unit,
    lyrics: LyricsUiState,
    onLyricsIntent: (LyricsIntent) -> Unit,
    panel: NowPlayingPanel,
    onPanelChange: (NowPlayingPanel) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 840.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onCollapse, shapes = IconButtonDefaults.shapes()) {
                        Icon(WavedotIcons.KeyboardArrowDown, contentDescription = "Collapse")
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Now playing", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    PanelSelector(panel, onPanelChange)
                }
                if (state.track == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing is playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@Column
                }
                if (wide) {
                    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        NowPlayingHero(state, onIntent, onAddToPlaylist, Modifier.weight(1f).fillMaxHeight())
                        when (panel) {
                            NowPlayingPanel.QUEUE -> QueuePane(state, onIntent, onAddToPlaylist, Modifier.weight(1f).fillMaxHeight())
                            NowPlayingPanel.LYRICS -> LyricsPane(lyrics, onLyricsIntent, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                } else if (panel == NowPlayingPanel.LYRICS) {
                    LyricsPane(lyrics, onLyricsIntent, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp))
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        SeekRow(state, onIntent)
                        TransportControls(state, onIntent, large = false)
                    }
                } else {
                    NowPlayingHero(state, onIntent, onAddToPlaylist, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun PanelSelector(panel: NowPlayingPanel, onPanelChange: (NowPlayingPanel) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        ToggleButton(
            checked = panel == NowPlayingPanel.QUEUE,
            onCheckedChange = { onPanelChange(NowPlayingPanel.QUEUE) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
        ) {
            Icon(WavedotIcons.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Up next")
        }
        ToggleButton(
            checked = panel == NowPlayingPanel.LYRICS,
            onCheckedChange = { onPanelChange(NowPlayingPanel.LYRICS) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
        ) {
            Icon(WavedotIcons.Lyrics, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Lyrics")
        }
    }
}

@Composable
private fun NowPlayingHero(
    state: PlayerUiState,
    onIntent: (PlayerIntent) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    modifier: Modifier,
) {
    val track = state.track ?: return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Artwork(
            seed = track.album?.title ?: track.title,
            url = track.artwork?.url,
            size = 280.dp,
            shape = MaterialShapes.Cookie12Sided.toShape(),
            spinning = state.isPlaying,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artistLine,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        SeekRow(state, onIntent, Modifier.widthIn(max = 520.dp))
        Spacer(Modifier.height(16.dp))
        TransportControls(state, onIntent, large = true)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 520.dp)) {
            FavoriteToggle(state.favorite, { onIntent(PlayerIntent.ToggleFavorite) })
            AddToPlaylistButton { onAddToPlaylist(listOf(track)) }
            VolumeControl(state, onIntent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QueuePane(
    state: PlayerUiState,
    onIntent: (PlayerIntent) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    modifier: Modifier,
) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onAddToPlaylist(state.queue) },
                    enabled = state.queue.isNotEmpty(),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(WavedotIcons.PlaylistAdd, contentDescription = "Save queue to playlist")
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(state.queue) { index, item ->
                    TrackListItem(
                        track = item,
                        index = index,
                        count = state.queue.size,
                        favorite = false,
                        playing = index == state.queuePosition,
                        onClick = { onIntent(PlayerIntent.SkipTo(index)) },
                        onToggleFavorite = null,
                        actions = listOf(TrackAction("Add to playlist", WavedotIcons.PlaylistAdd) { onAddToPlaylist(listOf(item)) }),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackTitles(state: PlayerUiState, modifier: Modifier) {
    val track = state.track ?: return
    Column(modifier) {
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Text(
            text = state.error ?: track.artistLine,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransportControls(state: PlayerUiState, onIntent: (PlayerIntent) -> Unit, large: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 4.dp)) {
        IconToggleButton(
            checked = state.shuffled,
            onCheckedChange = { onIntent(PlayerIntent.ToggleShuffle) },
            shapes = IconButtonDefaults.toggleableShapes(),
        ) {
            Icon(WavedotIcons.Shuffle, contentDescription = "Shuffle")
        }
        FilledTonalIconButton(
            onClick = { onIntent(PlayerIntent.Previous) },
            shapes = IconButtonDefaults.shapes(),
            modifier = if (large) Modifier.size(64.dp, 56.dp) else Modifier,
        ) {
            Icon(WavedotIcons.SkipPreviousFilled, contentDescription = "Previous")
        }
        PlayPauseButton(state, onIntent, large)
        FilledTonalIconButton(
            onClick = { onIntent(PlayerIntent.Next) },
            shapes = IconButtonDefaults.shapes(),
            modifier = if (large) Modifier.size(64.dp, 56.dp) else Modifier,
        ) {
            Icon(WavedotIcons.SkipNextFilled, contentDescription = "Next")
        }
        IconToggleButton(
            checked = state.repeat != RepeatMode.OFF,
            onCheckedChange = { onIntent(PlayerIntent.CycleRepeat) },
            shapes = IconButtonDefaults.toggleableShapes(),
        ) {
            Icon(
                imageVector = if (state.repeat == RepeatMode.ONE) WavedotIcons.RepeatOne else WavedotIcons.Repeat,
                contentDescription = "Repeat",
            )
        }
    }
}

@Composable
private fun PlayPauseButton(state: PlayerUiState, onIntent: (PlayerIntent) -> Unit, large: Boolean) {
    FilledIconButton(
        onClick = { onIntent(PlayerIntent.TogglePlay) },
        shape = if (state.isPlaying) MaterialTheme.shapes.large else CircleShape,
        modifier = if (large) Modifier.size(width = 104.dp, height = 72.dp) else Modifier.size(width = 56.dp, height = 44.dp),
    ) {
        Icon(
            imageVector = if (state.isPlaying) WavedotIcons.PauseFilled else WavedotIcons.PlayArrowFilled,
            contentDescription = if (state.isPlaying) "Pause" else "Play",
            modifier = Modifier.size(if (large) 36.dp else 24.dp),
        )
    }
}

@Composable
private fun SeekRow(state: PlayerUiState, onIntent: (PlayerIntent) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.duration ?: Duration.ZERO
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(state.position.toClock(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
        Slider(
            value = dragging ?: state.progress(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onIntent(PlayerIntent.Seek((duration.inWholeMilliseconds * it).toLong().milliseconds)) }
                dragging = null
            },
            enabled = duration > Duration.ZERO,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (state.isLive) "Live" else duration.toClock(),
            style = MaterialTheme.typography.labelMedium,
            color = if (state.isLive) MaterialTheme.colorScheme.primary else Color.Unspecified,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun PlaybackWave(state: PlayerUiState, modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(
        progress = { state.progress() },
        amplitude = { if (state.isPlaying) 1f else 0f },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun VolumeControl(state: PlayerUiState, onIntent: (PlayerIntent) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = WavedotIcons.VolumeUpFilled,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Slider(
            value = state.volume,
            onValueChange = { onIntent(PlayerIntent.SetVolume(it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun PlayerUiState.progress(): Float {
    val total = duration?.inWholeMilliseconds ?: return 0f
    if (total <= 0) return 0f
    return (position.inWholeMilliseconds.toFloat() / total).coerceIn(0f, 1f)
}

@Composable
private fun AddToPlaylistButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(WavedotIcons.PlaylistAdd, contentDescription = "Add to playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
