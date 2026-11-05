package space.wavedot.infra.playback.desktop

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVChannelLayout
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avformat.av_find_best_stream
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.ffmpeg.global.avutil.AV_TIME_BASE
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_uninit
import org.bytedeco.ffmpeg.global.avutil.av_dict_free
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_unref
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.ffmpeg.global.avutil.av_strerror
import org.bytedeco.ffmpeg.global.swresample.swr_alloc_set_opts2
import org.bytedeco.ffmpeg.global.swresample.swr_convert
import org.bytedeco.ffmpeg.global.swresample.swr_free
import org.bytedeco.ffmpeg.global.swresample.swr_get_out_samples
import org.bytedeco.ffmpeg.global.swresample.swr_init
import org.bytedeco.ffmpeg.swresample.SwrContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

class FfmpegException(message: String) : RuntimeException(message)

class FfmpegDecoder private constructor(
    private val format: AVFormatContext,
    private val codec: AVCodecContext,
    private val resampler: SwrContext,
    private val streamIndex: Int,
    val output: PcmFormat,
    val duration: Duration?,
) : AutoCloseable {
    private val packet: AVPacket = av_packet_alloc()
    private val frame: AVFrame = av_frame_alloc()
    private var nativeBuffer: BytePointer? = null
    private var nativeCapacitySamples = 0
    private var pending = ByteArray(0)
    private var pendingOffset = 0
    private var pendingLength = 0
    private var drainingDecoder = false
    private var finished = false
    private var closed = false

    fun read(target: ByteArray, offset: Int = 0, length: Int = target.size - offset): Int {
        while (pendingLength == 0) {
            if (finished || !decodeNext()) {
                finished = true
                return -1
            }
        }
        val count = minOf(length, pendingLength)
        System.arraycopy(pending, pendingOffset, target, offset, count)
        pendingOffset += count
        pendingLength -= count
        return count
    }

    fun seek(position: Duration) {
        val timestamp = position.inWholeMicroseconds
        check(av_seek_frame(format, -1, timestamp, AVSEEK_FLAG_BACKWARD) >= 0) { "Seek failed" }
        avcodec_flush_buffers(codec)
        pendingOffset = 0
        pendingLength = 0
        drainingDecoder = false
        finished = false
    }

    private fun decodeNext(): Boolean {
        while (true) {
            val received = avcodec_receive_frame(codec, frame)
            if (received >= 0) {
                convertFrame()
                av_frame_unref(frame)
                if (pendingLength > 0) return true
                continue
            }
            if (received == AVERROR_EOF) return false
            if (drainingDecoder) return false
            val read = av_read_frame(format, packet)
            if (read < 0) {
                avcodec_send_packet(codec, null as AVPacket?)
                drainingDecoder = true
                continue
            }
            if (packet.stream_index() == streamIndex) {
                val sent = avcodec_send_packet(codec, packet)
                if (sent < 0 && sent != AVERROR_EAGAIN) {
                    av_packet_unref(packet)
                    throw FfmpegException("Decoding failed: ${errorText(sent)}")
                }
            }
            av_packet_unref(packet)
        }
    }

    private fun convertFrame() {
        val inSamples = frame.nb_samples()
        val maxOut = swr_get_out_samples(resampler, inSamples).coerceAtLeast(inSamples)
        val buffer = ensureNativeBuffer(maxOut)
        val outPointers = PointerPointer<BytePointer>(1L).put(0, buffer)
        val converted = swr_convert(resampler, outPointers, maxOut, frame.extended_data(), inSamples)
        outPointers.close()
        if (converted < 0) throw FfmpegException("Resampling failed: ${errorText(converted)}")
        val bytes = converted * output.bytesPerFrame
        if (pending.size < bytes) pending = ByteArray(bytes)
        buffer.position(0).get(pending, 0, bytes)
        pendingOffset = 0
        pendingLength = bytes
    }

    private fun ensureNativeBuffer(samples: Int): BytePointer {
        val current = nativeBuffer
        if (current != null && nativeCapacitySamples >= samples) return current
        current?.close()
        nativeCapacitySamples = samples
        return BytePointer(samples.toLong() * output.bytesPerFrame).also { nativeBuffer = it }
    }

    override fun close() {
        if (closed) return
        closed = true
        av_packet_free(packet)
        av_frame_free(frame)
        swr_free(resampler)
        avcodec_free_context(codec)
        avformat_close_input(format)
        nativeBuffer?.close()
    }

    companion object {
        private val AVERROR_EAGAIN = -11

        init {
            av_log_set_level(AV_LOG_ERROR)
        }

        fun open(uri: String, headers: Map<String, String> = emptyMap(), maxChannels: Int = 2): FfmpegDecoder {
            val format = AVFormatContext(null)
            val options = AVDictionary(null)
            if (headers.isNotEmpty()) {
                av_dict_set(options, "headers", headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }, 0)
            }
            val opened = avformat_open_input(format, uri, null, options)
            av_dict_free(options)
            if (opened < 0) throw FfmpegException("Cannot open $uri: ${errorText(opened)}")
            try {
                check(avformat_find_stream_info(format, null as PointerPointer<*>?) >= 0) { "No stream info" }
                val streamIndex = av_find_best_stream(format, AVMEDIA_TYPE_AUDIO, -1, -1, null as PointerPointer<*>?, 0)
                if (streamIndex < 0) throw FfmpegException("No audio stream in $uri")
                val parameters = format.streams(streamIndex).codecpar()
                val decoder = avcodec_find_decoder(parameters.codec_id())
                    ?: throw FfmpegException("No decoder for codec ${parameters.codec_id()}")
                val codec = avcodec_alloc_context3(decoder)
                avcodec_parameters_to_context(codec, parameters)
                if (avcodec_open2(codec, decoder, null as AVDictionary?) < 0) {
                    avcodec_free_context(codec)
                    throw FfmpegException("Cannot open decoder")
                }
                val channels = codec.ch_layout().nb_channels().coerceIn(1, maxChannels)
                val outputFormat = PcmFormat(codec.sample_rate(), channels)
                val outLayout = AVChannelLayout()
                av_channel_layout_default(outLayout, channels)
                val resampler = SwrContext(null as Pointer?)
                val allocated = swr_alloc_set_opts2(
                    resampler, outLayout, AV_SAMPLE_FMT_S16, outputFormat.sampleRate,
                    codec.ch_layout(), codec.sample_fmt(), codec.sample_rate(), 0, null,
                )
                av_channel_layout_uninit(outLayout)
                if (allocated < 0 || swr_init(resampler) < 0) {
                    avcodec_free_context(codec)
                    throw FfmpegException("Cannot initialise resampler")
                }
                val duration = format.duration().takeIf { it != AV_NOPTS_VALUE && it > 0 }
                    ?.let { (it * 1_000_000L / AV_TIME_BASE).microseconds }
                return FfmpegDecoder(format, codec, resampler, streamIndex, outputFormat, duration)
            } catch (t: Throwable) {
                avformat_close_input(format)
                throw t
            }
        }

        private fun errorText(code: Int): String {
            val buffer = BytePointer(256L)
            av_strerror(code, buffer, 256L)
            return buffer.string.also { buffer.close() }
        }
    }
}
