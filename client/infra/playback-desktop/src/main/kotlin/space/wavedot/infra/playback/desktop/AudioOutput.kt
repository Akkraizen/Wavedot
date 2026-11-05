package space.wavedot.infra.playback.desktop

data class PcmFormat(
    val sampleRate: Int,
    val channels: Int,
) {
    val bytesPerFrame: Int get() = channels * BYTES_PER_SAMPLE

    companion object {
        const val BYTES_PER_SAMPLE: Int = 2
    }
}

interface AudioOutput : AutoCloseable {
    val playedFrames: Long

    fun open(format: PcmFormat)

    fun write(data: ByteArray, offset: Int, length: Int)

    fun start()

    fun pause()

    fun flush()

    fun drain()
}

fun interface AudioOutputFactory {
    fun create(): AudioOutput
}
