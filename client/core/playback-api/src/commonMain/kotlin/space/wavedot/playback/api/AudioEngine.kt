package space.wavedot.playback.api

import kotlinx.coroutines.flow.StateFlow
import space.wavedot.core.model.stream.StreamSource
import kotlin.time.Duration

enum class EngineStatus { IDLE, LOADING, READY, PLAYING, PAUSED, ENDED, ERROR }

data class EngineState(
    val status: EngineStatus = EngineStatus.IDLE,
    val position: Duration = Duration.ZERO,
    val duration: Duration? = null,
    val volume: Float = 1f,
    val error: String? = null,
)

interface AudioEngine : AutoCloseable {
    val state: StateFlow<EngineState>

    suspend fun load(source: StreamSource, startPosition: Duration = Duration.ZERO, playWhenReady: Boolean = true)

    fun play()

    fun pause()

    fun stop()

    fun seekTo(position: Duration)

    fun setVolume(volume: Float)
}
