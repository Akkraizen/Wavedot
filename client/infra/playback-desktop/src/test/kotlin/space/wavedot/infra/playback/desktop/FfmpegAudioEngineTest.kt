package space.wavedot.infra.playback.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.playback.api.EngineStatus
import space.wavedot.playback.api.StreamProxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FfmpegAudioEngineTest {
    private val proxy = object : StreamProxy {
        override suspend fun publish(source: StreamSource.Custom): String = error("unused")
        override fun revoke(url: String) = Unit
        override fun close() = Unit
    }

    @Test
    fun playsLocalFileToCompletion() = runBlocking {
        val output = RecordingOutput()
        val engine = FfmpegAudioEngine(proxy, { output })
        val file = TestAudio.sineWav(0.5)
        engine.load(StreamSource.LocalFile(file.absolutePath))
        withTimeout(10_000) { engine.state.first { it.status == EngineStatus.ENDED } }
        assertEquals(22_050L * 4, output.bytesWritten)
        engine.close()
    }

    @Test
    fun appliesVolumeInSoftware() = runBlocking {
        val output = RecordingOutput()
        val engine = FfmpegAudioEngine(proxy, { output })
        engine.setVolume(0.25f)
        engine.load(StreamSource.LocalFile(TestAudio.sineWav(0.3).absolutePath))
        withTimeout(10_000) { engine.state.first { it.status == EngineStatus.ENDED } }
        assertTrue(output.peak in 2_900..3_100, "peak ${output.peak}")
        engine.close()
    }

    @Test
    fun reportsErrorForMissingFile() = runBlocking {
        val engine = FfmpegAudioEngine(proxy, { RecordingOutput() })
        engine.load(StreamSource.LocalFile("/definitely/missing.flac"))
        assertEquals(EngineStatus.ERROR, engine.state.value.status)
        engine.close()
    }

    @Test
    fun loadWithoutAutoplayWaitsForPlay() = runBlocking {
        val output = RecordingOutput()
        val engine = FfmpegAudioEngine(proxy, { output })
        engine.load(StreamSource.LocalFile(TestAudio.sineWav(0.2).absolutePath), playWhenReady = false)
        assertEquals(EngineStatus.READY, engine.state.value.status)
        assertEquals(0L, output.bytesWritten)
        engine.play()
        withTimeout(10_000) { engine.state.first { it.status == EngineStatus.ENDED } }
        assertTrue(output.bytesWritten > 0)
        engine.close()
    }
}
