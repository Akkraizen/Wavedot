package space.wavedot.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.wavedot.core.domain.player.RepeatMode
import space.wavedot.ui.designsystem.ArtworkTile
import space.wavedot.ui.designsystem.TrackRow
import space.wavedot.ui.designsystem.toClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MiniPlayer(state: PlayerUiState, onIntent: (PlayerIntent) -> Unit, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val track = state.track ?: return
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        LinearProgressIndicator(
            progress = { state.progress() },
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkTile(track.album?.title ?: track.title, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    state.error ?: track.artistLine,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ControlButton("⏮") { onIntent(PlayerIntent.Previous) }
            ControlButton(if (state.isPlaying) "⏸" else "▶") { onIntent(PlayerIntent.TogglePlay) }
            ControlButton("⏭") { onIntent(PlayerIntent.Next) }
        }
    }
}

@Composable
fun NowPlayingScreen(state: PlayerUiState, onIntent: (PlayerIntent) -> Unit, onCollapse: () -> Unit) {
    val track = state.track
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onCollapse) { Text("⌄  Now playing") }
        }
        if (track == null) {
            Text("Nothing is playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Spacer(Modifier.height(16.dp))
        ArtworkTile(track.album?.title ?: track.title, 220.dp)
        Spacer(Modifier.height(24.dp))
        Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(track.artistLine, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        SeekBar(state, onIntent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton(if (state.shuffled) "🔀•" else "🔀") { onIntent(PlayerIntent.ToggleShuffle) }
            ControlButton("⏮") { onIntent(PlayerIntent.Previous) }
            ControlButton(if (state.isPlaying) "⏸" else "▶", large = true) { onIntent(PlayerIntent.TogglePlay) }
            ControlButton("⏭") { onIntent(PlayerIntent.Next) }
            ControlButton(
                when (state.repeat) {
                    RepeatMode.OFF -> "🔁"
                    RepeatMode.ALL -> "🔁•"
                    RepeatMode.ONE -> "🔂"
                },
            ) { onIntent(PlayerIntent.CycleRepeat) }
            ControlButton(if (state.favorite) "♥" else "♡") { onIntent(PlayerIntent.ToggleFavorite) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🔈", modifier = Modifier.padding(end = 8.dp))
            Slider(value = state.volume, onValueChange = { onIntent(PlayerIntent.SetVolume(it)) }, modifier = Modifier.weight(1f))
        }
        Text("Up next", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(state.queue) { index, item ->
                TrackRow(
                    track = item,
                    favorite = false,
                    playing = index == state.queuePosition,
                    onClick = { onIntent(PlayerIntent.SkipTo(index)) },
                    onToggleFavorite = {},
                )
            }
        }
    }
}

@Composable
private fun SeekBar(state: PlayerUiState, onIntent: (PlayerIntent) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.duration ?: Duration.ZERO
    Slider(
        value = dragging ?: state.progress(),
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onIntent(PlayerIntent.Seek((duration.inWholeMilliseconds * it).toLong().milliseconds)) }
            dragging = null
        },
        enabled = duration > Duration.ZERO,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(state.position.toClock(), style = MaterialTheme.typography.labelSmall)
        Text(duration.toClock(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ControlButton(symbol: String, large: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(symbol, fontSize = if (large) 32.sp else 20.sp)
    }
}

private fun PlayerUiState.progress(): Float {
    val total = duration?.inWholeMilliseconds ?: return 0f
    if (total <= 0) return 0f
    return (position.inWholeMilliseconds.toFloat() / total).coerceIn(0f, 1f)
}
