package space.wavedot.core.domain.library

import space.wavedot.core.common.Logger
import space.wavedot.core.common.NoopLogger
import space.wavedot.core.common.TimeProvider
import space.wavedot.core.common.warn
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.model.Track

class ToggleFavorite(
    private val favorites: FavoritesRepository,
    private val registry: MediaSourceRegistry,
    private val time: TimeProvider,
    private val logger: Logger = NoopLogger,
) {
    suspend operator fun invoke(track: Track): Boolean {
        val now = time.nowMillis()
        val becameFavorite = !favorites.isFavorite(track.id)
        if (becameFavorite) favorites.add(track, now) else favorites.remove(track.id, now)
        mirrorToSource(track, becameFavorite)
        return becameFavorite
    }

    private suspend fun mirrorToSource(track: Track, liked: Boolean) {
        val library = registry.find(track.id.source)?.library ?: return
        runCatching {
            if (liked) library.like(track.id) else library.unlike(track.id)
        }.onFailure { logger.warn(TAG, "Could not mirror like state for ${track.id}", it) }
    }

    private companion object {
        const val TAG = "ToggleFavorite"
    }
}
