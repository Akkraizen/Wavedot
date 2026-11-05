package space.wavedot.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.playback.api.AudioEngine
import space.wavedot.playback.api.EngineState
import space.wavedot.playback.api.EngineStatus
import kotlin.time.Duration

class FakeAudioEngine : AudioEngine {
    private val mutableState = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = mutableState

    val loaded = mutableListOf<StreamSource>()
    var failNextLoad: Throwable? = null

    override suspend fun load(source: StreamSource, startPosition: Duration, playWhenReady: Boolean) {
        failNextLoad?.let {
            failNextLoad = null
            throw it
        }
        loaded += source
        mutableState.value = EngineState(
            status = if (playWhenReady) EngineStatus.PLAYING else EngineStatus.READY,
            position = startPosition,
            duration = null,
            volume = mutableState.value.volume,
        )
    }

    override fun play() = mutableState.update { it.copy(status = EngineStatus.PLAYING) }

    override fun pause() = mutableState.update { it.copy(status = EngineStatus.PAUSED) }

    override fun stop() = mutableState.update { it.copy(status = EngineStatus.IDLE, position = Duration.ZERO) }

    override fun seekTo(position: Duration) = mutableState.update { it.copy(position = position) }

    override fun setVolume(volume: Float) = mutableState.update { it.copy(volume = volume) }

    fun finishCurrent() = mutableState.update { it.copy(status = EngineStatus.ENDED) }

    override fun close() = Unit
}
