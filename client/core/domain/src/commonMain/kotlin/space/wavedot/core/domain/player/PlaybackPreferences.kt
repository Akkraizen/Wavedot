package space.wavedot.core.domain.player

import space.wavedot.core.model.AudioQuality

interface PlaybackPreferences {
    suspend fun preferredQuality(): AudioQuality
}

class FixedPlaybackPreferences(private val quality: AudioQuality = AudioQuality.HIGH) : PlaybackPreferences {
    override suspend fun preferredQuality(): AudioQuality = quality
}
