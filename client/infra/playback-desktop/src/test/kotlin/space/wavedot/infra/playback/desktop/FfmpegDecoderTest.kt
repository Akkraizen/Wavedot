package space.wavedot.infra.playback.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class FfmpegDecoderTest {
    @Test
    fun decodesWholeFileToPcm() {
        val file = TestAudio.sineWav(1.0)
        FfmpegDecoder.open(file.absolutePath).use { decoder ->
            assertEquals(PcmFormat(44_100, 2), decoder.output)
            val duration = assertNotNull(decoder.duration)
            assertTrue(duration in 990.milliseconds..1010.milliseconds, "duration $duration")
            var total = 0L
            val buffer = ByteArray(8192)
            while (true) {
                val read = decoder.read(buffer)
                if (read < 0) break
                total += read
            }
            assertEquals(44_100L * 4, total)
        }
    }

    @Test
    fun seekSkipsDecodedAudio() {
        val file = TestAudio.sineWav(1.0)
        FfmpegDecoder.open(file.absolutePath).use { decoder ->
            decoder.seek(500.milliseconds)
            var total = 0L
            val buffer = ByteArray(8192)
            while (true) {
                val read = decoder.read(buffer)
                if (read < 0) break
                total += read
            }
            assertTrue(total in 80_000L..100_000L, "decoded $total bytes after seek")
        }
    }

    @Test
    fun downmixesToRequestedChannelCount() {
        val file = TestAudio.sineWav(0.2, channels = 2)
        FfmpegDecoder.open(file.absolutePath, maxChannels = 1).use { decoder ->
            assertEquals(1, decoder.output.channels)
        }
    }
}
