package space.wavedot.infra.hub

import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import space.wavedot.core.domain.library.Favorite
import space.wavedot.core.domain.library.FavoriteChange
import space.wavedot.core.domain.sync.SyncRecord
import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Artwork
import space.wavedot.core.model.ArtworkReference
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Track
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.hub.ChangeDto
import space.wavedot.protocol.hub.FavoritePayloadDto
import space.wavedot.protocol.hub.SyncCollections
import kotlin.time.Duration.Companion.milliseconds

internal object SyncRecordMapper {
    fun toDto(record: SyncRecord): ChangeDto = when (record) {
        is SyncRecord.Favorite -> {
            val change = record.change
            ChangeDto(
                collection = SyncCollections.FAVORITES,
                key = change.id.toString(),
                deleted = change.deleted,
                payload = change.favorite?.let { ProtocolJson.encodeToJsonElement(it.toPayload()) },
                updatedAt = change.updatedAt,
            )
        }
    }

    fun fromDto(dto: ChangeDto): SyncRecord? = when (dto.collection) {
        SyncCollections.FAVORITES -> runCatching { favoriteFromDto(dto) }.getOrNull()
        else -> null
    }

    private fun favoriteFromDto(dto: ChangeDto): SyncRecord.Favorite {
        val id = MediaId.parse(dto.key)
        val favorite = if (dto.deleted) null else dto.payload
            ?.let { ProtocolJson.decodeFromJsonElement<FavoritePayloadDto>(it) }
            ?.toFavorite(id)
        return SyncRecord.Favorite(FavoriteChange(id, favorite, dto.updatedAt))
    }

    private fun Favorite.toPayload() = FavoritePayloadDto(
        title = track.title,
        artists = track.artists.map { it.name },
        album = track.album?.title,
        durationMs = track.duration?.inWholeMilliseconds,
        artworkUrl = track.artwork?.url?.takeIf(::isPortableArtwork),
        addedAt = addedAt,
    )

    private fun FavoritePayloadDto.toFavorite(id: MediaId) = Favorite(
        track = Track(
            id = id,
            title = title,
            artists = artists.map { ArtistRef(null, it) },
            album = album?.let { AlbumRef(null, it) },
            duration = durationMs?.milliseconds,
            artwork = artworkUrl?.takeIf(::isPortableArtwork)?.let(::Artwork),
        ),
        addedAt = addedAt,
    )

    private fun isPortableArtwork(url: String): Boolean =
        ArtworkReference.matches(url) || url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}
