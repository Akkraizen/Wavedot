package space.wavedot.infra.playback.desktop

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

class JavaSoundOutput(private val bufferMillis: Int = 250) : AudioOutput {
    private var line: SourceDataLine? = null
    private var format: PcmFormat? = null
    private var frameBase: Long = 0

    override val playedFrames: Long
        get() = (line?.longFramePosition ?: 0L) - frameBase

    override fun open(format: PcmFormat) {
        close()
        val audioFormat = AudioFormat(format.sampleRate.toFloat(), 16, format.channels, true, false)
        val bufferBytes = format.sampleRate * format.bytesPerFrame * bufferMillis / 1000
        line = AudioSystem.getSourceDataLine(audioFormat).apply { open(audioFormat, bufferBytes) }
        this.format = format
        frameBase = 0
    }

    override fun write(data: ByteArray, offset: Int, length: Int) {
        requireNotNull(line) { "Output is not open" }.write(data, offset, length)
    }

    override fun start() {
        line?.start()
    }

    override fun pause() {
        line?.stop()
    }

    override fun flush() {
        val current = line ?: return
        current.flush()
        frameBase = current.longFramePosition
    }

    override fun drain() {
        line?.drain()
    }

    override fun close() {
        line?.run {
            stop()
            flush()
            close()
        }
        line = null
        format = null
    }
}
