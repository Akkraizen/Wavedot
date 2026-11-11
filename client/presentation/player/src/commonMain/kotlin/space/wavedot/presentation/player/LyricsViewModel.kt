package space.wavedot.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import space.wavedot.core.domain.lyrics.GetLyrics
import space.wavedot.core.domain.lyrics.LyricsResult
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.model.Track
import space.wavedot.core.model.lyrics.LyricLine
import space.wavedot.core.model.lyrics.Lyrics

sealed interface LyricsContent {
    data object Idle : LyricsContent
    data object Loading : LyricsContent
    data object Unsupported : LyricsContent
    data object NotFound : LyricsContent
    data class Failed(val message: String) : LyricsContent
    data class Ready(val lyrics: Lyrics) : LyricsContent
}

data class LyricsUiState(
    val content: LyricsContent = LyricsContent.Idle,
    val activeIndex: Int = -1,
)

sealed interface LyricsIntent {
    data class SeekToLine(val line: LyricLine) : LyricsIntent
    data object Retry : LyricsIntent
}

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModel(
    private val player: PlayerController,
    private val getLyrics: GetLyrics,
) : ViewModel() {
    private val attempt = MutableStateFlow(0)

    private val content: Flow<LyricsContent> =
        combine(player.state.map { it.current }.distinctUntilChanged { old, new -> old?.id == new?.id }, attempt) { track, _ -> track }
            .flatMapLatest(::load)

    val state: StateFlow<LyricsUiState> =
        combine(content, player.state.map { it.engine.position }) { content, position ->
            val active = (content as? LyricsContent.Ready)?.lyrics?.activeLineIndex(position) ?: -1
            LyricsUiState(content, active)
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUiState())

    fun onIntent(intent: LyricsIntent) {
        when (intent) {
            is LyricsIntent.SeekToLine -> intent.line.start?.let(player::seekTo)
            LyricsIntent.Retry -> attempt.update { it + 1 }
        }
    }

    private fun load(track: Track?): Flow<LyricsContent> = flow {
        if (track == null) {
            emit(LyricsContent.Idle)
            return@flow
        }
        emit(LyricsContent.Loading)
        emit(
            when (val result = getLyrics(track)) {
                is LyricsResult.Found -> LyricsContent.Ready(result.lyrics)
                LyricsResult.NotFound -> LyricsContent.NotFound
                LyricsResult.Unsupported -> LyricsContent.Unsupported
                is LyricsResult.Failed -> LyricsContent.Failed(result.message)
            },
        )
    }
}
