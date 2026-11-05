package space.wavedot.core.domain.player

import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.Track
import space.wavedot.core.model.stream.StreamSource

class ResolveStream(private val registry: MediaSourceRegistry) {
    suspend operator fun invoke(track: Track, quality: AudioQuality): StreamSource {
        val source = registry.require(track.id.source)
        val streaming = source.streaming
            ?: throw WavedotException(WavedotError.Unsupported("streaming from ${source.descriptor.displayName}"))
        val effective = pickQuality(quality, streaming.supportedQualities)
        return streaming.resolve(track.id, effective)
    }

    internal fun pickQuality(requested: AudioQuality, supported: Set<AudioQuality>): AudioQuality {
        if (supported.isEmpty() || requested in supported) return requested
        val lowerOrEqual = supported.filter { it <= requested }
        return lowerOrEqual.maxOrNull() ?: supported.min()
    }
}
