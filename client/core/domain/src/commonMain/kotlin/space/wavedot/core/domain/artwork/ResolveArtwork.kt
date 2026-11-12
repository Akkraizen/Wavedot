package space.wavedot.core.domain.artwork

import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.model.ArtworkReference

class ResolveArtwork(private val registry: MediaSourceRegistry) {
    suspend operator fun invoke(url: String): String? {
        if (!ArtworkReference.matches(url)) return url
        val reference = ArtworkReference.parse(url) ?: return null
        return registry.find(reference.source)?.artwork?.resolve(reference.key)
    }
}
