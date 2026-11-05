package space.wavedot.infra.playback.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import space.wavedot.core.model.stream.ByteStream
import space.wavedot.core.model.stream.ByteStreamOpener
import space.wavedot.core.model.stream.StreamSource
import space.wavedot.infra.streamproxy.LoopbackStreamProxy
import space.wavedot.playback.api.EngineStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxiedPlaybackTest {
    @Test
    fun playsCustomStreamThroughLoopbackProxy() = runBlocking {
        val bytes = TestAudio.sineWav(0.5).readBytes()
        val source = StreamSource.Custom("audio/wav", ByteStreamOpener { offset ->
            object : ByteStream {
                private var position = offset.toInt()
                override val totalLength: Long = bytes.size.toLong()

                override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (position >= bytes.size) return -1
                    val count = minOf(length, bytes.size - position)
                    bytes.copyInto(buffer, offset, position, position + count)
                    position += count
                    return count
                }

                override fun close() = Unit
            }
        })
        val proxy = LoopbackStreamProxy()
        val output = RecordingOutput()
        val engine = FfmpegAudioEngine(proxy, { output })
        engine.load(source)
        withTimeout(10_000) { engine.state.first { it.status == EngineStatus.ENDED } }
        assertEquals(22_050L * 4, output.bytesWritten)
        engine.close()
        proxy.close()
    }
}
