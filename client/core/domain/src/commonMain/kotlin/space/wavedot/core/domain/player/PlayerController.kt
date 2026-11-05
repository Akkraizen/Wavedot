package space.wavedot.core.domain.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import space.wavedot.core.common.Logger
import space.wavedot.core.common.NoopLogger
import space.wavedot.core.common.toWavedotError
import space.wavedot.core.common.warn
import space.wavedot.core.model.Track
import space.wavedot.playback.api.AudioEngine
import space.wavedot.playback.api.EngineState
import space.wavedot.playback.api.EngineStatus
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class PlayerState(
    val queue: PlaybackQueue = PlaybackQueue(),
    val engine: EngineState = EngineState(),
    val error: String? = null,
) {
    val current: Track? get() = queue.current
    val isPlaying: Boolean get() = engine.status == EngineStatus.PLAYING
}

class PlayerController(
    private val engine: AudioEngine,
    private val resolveStream: ResolveStream,
    private val preferences: PlaybackPreferences,
    private val scope: CoroutineScope,
    private val logger: Logger = NoopLogger,
    private val random: Random = Random.Default,
) {
    private val queue = MutableStateFlow(PlaybackQueue())
    private val error = MutableStateFlow<String?>(null)
    private var loadJob: Job? = null

    val state: StateFlow<PlayerState> = combine(queue, engine.state, error) { q, e, err ->
        PlayerState(q, e, err ?: e.error)
    }.stateIn(scope, SharingStarted.Eagerly, PlayerState())

    init {
        engine.state
            .distinctUntilChangedBy { it.status }
            .filter { it.status == EngineStatus.ENDED }
            .onEach { advance(auto = true) }
            .launchIn(scope)
    }

    fun play(tracks: List<Track>, startIndex: Int = 0) {
        val shuffle = queue.value.shuffled
        val repeat = queue.value.repeat
        queue.value = PlaybackQueue.of(tracks, startIndex, shuffle, repeat, random)
        loadCurrent()
    }

    fun enqueue(tracks: List<Track>) {
        val wasEmpty = queue.value.isEmpty
        queue.update { it.append(tracks) }
        if (wasEmpty) loadCurrent(playWhenReady = false)
    }

    fun playNext(track: Track) {
        val wasEmpty = queue.value.isEmpty
        queue.update { it.playNext(track) }
        if (wasEmpty) loadCurrent()
    }

    fun togglePlayPause() {
        when (engine.state.value.status) {
            EngineStatus.PLAYING -> engine.pause()
            EngineStatus.READY, EngineStatus.PAUSED -> engine.play()
            EngineStatus.ENDED, EngineStatus.IDLE, EngineStatus.ERROR -> if (!queue.value.isEmpty) loadCurrent()
            EngineStatus.LOADING -> Unit
        }
    }

    fun next() = advance(auto = false)

    fun previous() {
        if (engine.state.value.position > RESTART_THRESHOLD) {
            engine.seekTo(Duration.ZERO)
            return
        }
        queue.update { it.previous() }
        loadCurrent()
    }

    fun skipTo(orderIndex: Int) {
        queue.update { it.skipTo(orderIndex) }
        loadCurrent()
    }

    fun removeAt(orderIndex: Int) {
        val before = queue.value
        val after = before.remove(orderIndex)
        queue.value = after
        when {
            after.isEmpty -> engine.stop()
            orderIndex == before.position -> loadCurrent()
        }
    }

    fun seekTo(position: Duration) = engine.seekTo(position)

    fun setVolume(volume: Float) = engine.setVolume(volume.coerceIn(0f, 1f))

    fun setRepeat(mode: RepeatMode) = queue.update { it.withRepeat(mode) }

    fun cycleRepeat() = queue.update {
        it.withRepeat(RepeatMode.entries[(it.repeat.ordinal + 1) % RepeatMode.entries.size])
    }

    fun setShuffle(enabled: Boolean) = queue.update { it.withShuffle(enabled, random) }

    fun stop() {
        loadJob?.cancel()
        engine.stop()
    }

    private fun advance(auto: Boolean) {
        val next = queue.value.next(auto)
        if (next == null) {
            if (!auto) engine.stop()
            return
        }
        queue.value = next
        loadCurrent()
    }

    private fun loadCurrent(playWhenReady: Boolean = true) {
        val track = queue.value.current ?: return
        loadJob?.cancel()
        error.value = null
        loadJob = scope.launch {
            try {
                val source = resolveStream(track, preferences.preferredQuality())
                engine.load(source, playWhenReady = playWhenReady)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                logger.warn(TAG, "Failed to load ${track.id}", t)
                error.value = t.toWavedotError().message
            }
        }
    }

    private companion object {
        const val TAG = "PlayerController"
        val RESTART_THRESHOLD = 3.seconds
    }
}
