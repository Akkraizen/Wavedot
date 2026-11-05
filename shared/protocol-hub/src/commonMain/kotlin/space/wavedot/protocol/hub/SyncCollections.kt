package space.wavedot.protocol.hub

object SyncCollections {
    const val FAVORITES: String = "favorites"
    const val PLAYLISTS: String = "playlists"
    const val HISTORY: String = "history"
    const val SETTINGS: String = "settings"

    val ALL: Set<String> = setOf(FAVORITES, PLAYLISTS, HISTORY, SETTINGS)
}

@kotlinx.serialization.Serializable
data class FavoritePayloadDto(
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    val addedAt: Long,
)
