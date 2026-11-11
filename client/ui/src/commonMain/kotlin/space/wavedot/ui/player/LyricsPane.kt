package space.wavedot.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import space.wavedot.core.model.lyrics.LyricLine
import space.wavedot.core.model.lyrics.Lyrics
import space.wavedot.presentation.player.LyricsContent
import space.wavedot.presentation.player.LyricsIntent
import space.wavedot.presentation.player.LyricsUiState
import space.wavedot.ui.designsystem.WavedotIcons

@Composable
fun LyricsPane(state: LyricsUiState, onIntent: (LyricsIntent) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        when (val content = state.content) {
            LyricsContent.Idle -> LyricsMessage("Nothing is playing", null)
            LyricsContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(64.dp))
            }
            LyricsContent.Unsupported -> LyricsMessage(
                title = "Lyrics are not available here",
                message = "Lyrics are shown for tracks from streaming services such as Yandex Music.",
            )
            LyricsContent.NotFound -> LyricsMessage("No lyrics found", "Neither the track's service nor LRCLIB has lyrics for this track.")
            is LyricsContent.Failed -> LyricsMessage("Could not load lyrics", content.message) {
                FilledTonalButton(onClick = { onIntent(LyricsIntent.Retry) }, shapes = ButtonDefaults.shapes()) {
                    Text("Try again")
                }
            }
            is LyricsContent.Ready -> LyricsLines(content.lyrics, state.activeIndex, onIntent)
        }
    }
}

@Composable
private fun LyricsLines(lyrics: Lyrics, activeIndex: Int, onIntent: (LyricsIntent) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val viewport = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(activeIndex, scrollOffset = -viewport / 3)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(if (lyrics.synced) 14.dp else 6.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            LyricRow(line, index, activeIndex, lyrics.synced) { onIntent(LyricsIntent.SeekToLine(line)) }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Lyrics: ${lyrics.provider}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            lyrics.credits?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, index: Int, activeIndex: Int, synced: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val target = when {
        !synced -> scheme.onSurface
        index == activeIndex -> scheme.primary
        index < activeIndex -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> scheme.onSurfaceVariant
    }
    val color by animateColorAsState(target, MaterialTheme.motionScheme.defaultEffectsSpec())
    val text = line.text.ifBlank { if (synced) "♪" else "" }
    Text(
        text = text,
        color = color,
        style = if (synced) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        fontWeight = if (synced && index == activeIndex) FontWeight.Bold else if (synced) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (synced && line.start != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
    )
}

@Composable
private fun LyricsMessage(title: String, message: String?, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(WavedotIcons.Lyrics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        action?.let {
            Spacer(Modifier.height(16.dp))
            it()
        }
    }
}
