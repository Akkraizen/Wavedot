package space.wavedot.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.wavedot.core.domain.library.FavoritesRepository
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.domain.player.RepeatMode
import space.wavedot.core.model.Track
import space.wavedot.playback.api.EngineStatus
import kotlin.time.Duration

data class PlayerUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val position: Duration = Duration.ZERO,
    val duration: Duration? = null,
    val volume: Float = 1f,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffled: Boolean = false,
    val favorite: Boolean = false,
    val queue: List<Track> = emptyList(),
    val queuePosition: Int = -1,
    val error: String? = null,
) {
    val isLive: Boolean get() = track != null && duration == null
}

sealed interface PlayerIntent {
    data object TogglePlay : PlayerIntent
    data object Next : PlayerIntent
    data object Previous : PlayerIntent
    data object CycleRepeat : PlayerIntent
    data object ToggleShuffle : PlayerIntent
    data object ToggleFavorite : PlayerIntent
    data class Seek(val position: Duration) : PlayerIntent
    data class SetVolume(val volume: Float) : PlayerIntent
    data class SkipTo(val index: Int) : PlayerIntent
}

class PlayerViewModel(
    private val controller: PlayerController,
    favorites: FavoritesRepository,
    private val toggleFavorite: ToggleFavorite,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = combine(controller.state, favorites.observeIds()) { player, favoriteIds ->
        PlayerUiState(
            track = player.current,
            isPlaying = player.engine.status == EngineStatus.PLAYING,
            isLoading = player.engine.status == EngineStatus.LOADING,
            position = player.engine.position,
            duration = player.engine.duration ?: player.current?.duration,
            volume = player.engine.volume,
            repeat = player.queue.repeat,
            shuffled = player.queue.shuffled,
            favorite = player.current?.id in favoriteIds,
            queue = player.queue.orderedTracks,
            queuePosition = player.queue.position,
            error = player.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.TogglePlay -> controller.togglePlayPause()
            PlayerIntent.Next -> controller.next()
            PlayerIntent.Previous -> controller.previous()
            PlayerIntent.CycleRepeat -> controller.cycleRepeat()
            PlayerIntent.ToggleShuffle -> controller.setShuffle(!state.value.shuffled)
            PlayerIntent.ToggleFavorite -> state.value.track?.let { viewModelScope.launch { toggleFavorite(it) } }
            is PlayerIntent.Seek -> controller.seekTo(intent.position)
            is PlayerIntent.SetVolume -> controller.setVolume(intent.volume)
            is PlayerIntent.SkipTo -> controller.skipTo(intent.index)
        }
    }
}
