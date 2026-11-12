package space.wavedot.infra.playback.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.playback.api.AudioEngine
import space.wavedot.playback.api.EngineState
import space.wavedot.playback.api.EngineStatus
import space.wavedot.playback.api.StreamProxy
import space.wavedot.playback.api.toPlayableUri
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

class FfmpegAudioEngine(
    private val proxy: StreamProxy,
    private val outputFactory: AudioOutputFactory = AudioOutputFactory { JavaSoundOutput() },
    private val positionInterval: Duration = 200.milliseconds,
) : AudioEngine {
    private val decodeThread: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { Thread(it, "wavedot-decoder").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + decodeThread)
    private val mutableState = MutableStateFlow(EngineState())
    private val playing = MutableStateFlow(false)
    private val pendingSeek = AtomicReference<Duration?>(null)
    private val loadMutex = Mutex()

    @Volatile private var volume: Float = 1f
    @Volatile private var output: AudioOutput? = null
    @Volatile private var basePosition: Duration = Duration.ZERO
    @Volatile private var sampleRate: Int = 0
    private var session: Job? = null
    private var publishedUrl: String? = null

    override val state: StateFlow<EngineState> = mutableState.asStateFlow()

    override suspend fun load(source: StreamSource, startPosition: Duration, playWhenReady: Boolean) = loadMutex.withLock {
        session?.cancelAndJoin()
        releasePublished()
        playing.value = false
        mutableState.value = EngineState(status = EngineStatus.LOADING, volume = volume)
        try {
            val uri = source.toPlayableUri(proxy)
            if (source is StreamSource.Custom) publishedUrl = uri.value
            val decoder = withContext(decodeThread) { FfmpegDecoder.open(uri.value, uri.headers, input = if (uri.isLocal) FfmpegInput.LOCAL_FILE else FfmpegInput.NETWORK)
            }
            if (startPosition > Duration.ZERO) withContext(decodeThread) { decoder.seek(startPosition) }
            val out = outputFactory.create().apply { open(decoder.output) }
            output = out
            sampleRate = decoder.output.sampleRate
            basePosition = startPosition
            pendingSeek.set(null)
            mutableState.value = EngineState(EngineStatus.READY, startPosition, decoder.duration, volume)
            session = scope.launch { runSession(decoder, out) }
            if (playWhenReady) play()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            mutableState.value = EngineState(EngineStatus.ERROR, volume = volume, error = t.message ?: "Playback failed")
        }
    }

    override fun play() {
        val status = mutableState.value.status
        if (status != EngineStatus.READY && status != EngineStatus.PAUSED) return
        output?.start()
        playing.value = true
        mutableState.update { it.copy(status = EngineStatus.PLAYING) }
    }

    override fun pause() {
        if (mutableState.value.status != EngineStatus.PLAYING) return
        playing.value = false
        output?.pause()
        mutableState.update { it.copy(status = EngineStatus.PAUSED, position = currentPosition()) }
    }

    override fun stop() {
        playing.value = false
        session?.cancel()
        session = null
        releasePublished()
        mutableState.value = EngineState(volume = volume)
    }

    override fun seekTo(position: Duration) {
        if (output == null) return
        pendingSeek.set(position.coerceAtLeast(Duration.ZERO))
        mutableState.update { it.copy(position = position) }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        mutableState.update { it.copy(volume = this.volume) }
    }

    override fun close() {
        stop()
        scope.launch { }.invokeOnCompletion { decodeThread.close() }
    }

    private suspend fun runSession(decoder: FfmpegDecoder, out: AudioOutput) {
        val ticker = scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            while (isActive) {
                delay(positionInterval)
                if (mutableState.value.status == EngineStatus.PLAYING) {
                    mutableState.update { it.copy(position = currentPosition()) }
                }
            }
        }
        val buffer = ByteArray(CHUNK_BYTES)
        try {
            while (true) {
                if (!playing.value) playing.first { it }
                pendingSeek.getAndSet(null)?.let { target ->
                    decoder.seek(target)
                    out.flush()
                    basePosition = target
                }
                val read = decoder.read(buffer)
                if (read < 0) break
                applyVolume(buffer, read)
                out.write(buffer, 0, read)
            }
            out.drain()
            playing.value = false
            mutableState.update { it.copy(status = EngineStatus.ENDED, position = it.duration ?: currentPosition()) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            playing.value = false
            mutableState.update { it.copy(status = EngineStatus.ERROR, error = t.message ?: "Decoding failed") }
        } finally {
            ticker.cancel()
            runCatching { out.close() }
            runCatching { decoder.close() }
            if (output === out) output = null
        }
    }

    private fun currentPosition(): Duration {
        val rate = sampleRate
        val frames = output?.playedFrames ?: return mutableState.value.position
        if (rate <= 0) return basePosition
        return basePosition + (frames * 1_000_000L / rate).microseconds
    }

    private fun applyVolume(buffer: ByteArray, length: Int) {
        val gain = volume
        if (gain >= 0.999f) return
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = scaled.toByte()
            buffer[i + 1] = (scaled shr 8).toByte()
            i += 2
        }
    }

    private fun releasePublished() {
        publishedUrl?.let(proxy::revoke)
        publishedUrl = null
    }

    private companion object {
        const val CHUNK_BYTES = 16 * 1024
    }
}
