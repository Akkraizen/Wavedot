package space.wavedot.infra.playback.android

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.playback.api.AudioEngine
import space.wavedot.playback.api.EngineState
import space.wavedot.playback.api.EngineStatus
import space.wavedot.playback.api.StreamProxy
import space.wavedot.playback.api.toPlayableUri
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
class Media3AudioEngine(
    private val context: Context,
    private val proxy: StreamProxy,
) : AudioEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(EngineState())
    private var publishedUrl: String? = null

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    override val state: StateFlow<EngineState> = mutableState.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publishState()

            override fun onPlayerError(error: PlaybackException) {
                mutableState.update { it.copy(status = EngineStatus.ERROR, error = error.message) }
            }
        })
        scope.launch {
            while (isActive) {
                delay(POSITION_INTERVAL_MS)
                if (player.isPlaying) publishState()
            }
        }
    }

    override suspend fun load(source: StreamSource, startPosition: Duration, playWhenReady: Boolean) {
        val uri = source.toPlayableUri(proxy)
        withContext(Dispatchers.Main.immediate) {
            publishedUrl?.let(proxy::revoke)
            publishedUrl = if (source is StreamSource.Custom) uri.value else null
            val httpFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(uri.headers)
            val mediaSource = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context, httpFactory))
                .createMediaSource(MediaItem.Builder().setUri(uri.value).setMimeType(uri.mimeType).build())
            player.setMediaSource(mediaSource, startPosition.inWholeMilliseconds)
            player.playWhenReady = playWhenReady
            player.prepare()
            mutableState.value = EngineState(EngineStatus.LOADING, startPosition, volume = player.volume)
        }
    }

    override fun play() = onMain { player.play() }

    override fun pause() = onMain { player.pause() }

    override fun stop() = onMain {
        player.stop()
        player.clearMediaItems()
        publishedUrl?.let(proxy::revoke)
        publishedUrl = null
        mutableState.value = EngineState(volume = player.volume)
    }

    override fun seekTo(position: Duration) = onMain { player.seekTo(position.inWholeMilliseconds) }

    override fun setVolume(volume: Float) = onMain {
        player.volume = volume
        publishState()
    }

    override fun close() = onMain {
        player.release()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun onMain(block: () -> Unit) {
        scope.launch { block() }
    }

    private fun publishState() {
        val status = when {
            player.playerError != null -> EngineStatus.ERROR
            player.playbackState == Player.STATE_ENDED -> EngineStatus.ENDED
            player.playbackState == Player.STATE_BUFFERING -> if (player.playWhenReady) EngineStatus.PLAYING else EngineStatus.LOADING
            player.playbackState == Player.STATE_READY -> if (player.playWhenReady) EngineStatus.PLAYING else EngineStatus.PAUSED
            else -> EngineStatus.IDLE
        }
        val duration = player.duration.takeIf { it != C.TIME_UNSET }?.milliseconds
        mutableState.update {
            it.copy(
                status = status,
                position = player.currentPosition.milliseconds,
                duration = duration,
                volume = player.volume,
                error = player.playerError?.message,
            )
        }
    }

    private companion object {
        const val POSITION_INTERVAL_MS = 250L
    }
}
