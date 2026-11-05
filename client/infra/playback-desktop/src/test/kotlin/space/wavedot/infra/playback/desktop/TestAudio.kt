package space.wavedot.infra.playback.desktop

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin

object TestAudio {
    fun sineWav(seconds: Double, sampleRate: Int = 44_100, channels: Int = 2): File {
        val frames = (seconds * sampleRate).toInt()
        val dataSize = frames * channels * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
        buffer.putInt(sampleRate).putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataSize)
        for (frame in 0 until frames) {
            val value = (sin(2 * PI * 440 * frame / sampleRate) * 12_000).toInt().toShort()
            repeat(channels) { buffer.putShort(value) }
        }
        return Files.createTempFile("wavedot", ".wav").toFile().apply {
            writeBytes(buffer.array())
            deleteOnExit()
        }
    }
}

class RecordingOutput : AudioOutput {
    var format: PcmFormat? = null
    var bytesWritten = 0L
    var peak = 0
    var started = false
    var flushes = 0

    override val playedFrames: Long get() = format?.let { bytesWritten / it.bytesPerFrame } ?: 0L

    override fun open(format: PcmFormat) {
        this.format = format
    }

    override fun write(data: ByteArray, offset: Int, length: Int) {
        var i = offset
        while (i + 1 < offset + length) {
            val sample = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort().toInt()
            peak = maxOf(peak, kotlin.math.abs(sample))
            i += 2
        }
        bytesWritten += length
    }

    override fun start() {
        started = true
    }

    override fun pause() {
        started = false
    }

    override fun flush() {
        flushes++
        bytesWritten = 0
    }

    override fun drain() = Unit

    override fun close() = Unit
}
