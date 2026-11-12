package space.wavedot.plugin.audius

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import space.wavedot.core.contract.ui.PluginAccent
import space.wavedot.core.contract.ui.PluginSection
import space.wavedot.core.contract.ui.PluginTrackAction
import space.wavedot.core.contract.ui.PluginUi
import space.wavedot.core.contract.ui.UiEffect
import space.wavedot.core.contract.ui.UiIcon
import space.wavedot.core.contract.ui.UiNode
import space.wavedot.core.contract.ui.UiValueAction
import space.wavedot.core.model.Track

internal class AudiusUi(private val api: AudiusApi) {
    fun build(): PluginUi = PluginUi(
        librarySections = listOf(PluginSection(TRENDING_ID, "Trending on Audius", trending())),
        trackActions = listOf(
            PluginTrackAction(
                id = MORE_FROM_ARTIST_ID,
                label = "More from this artist",
                icon = UiIcon.ARTIST,
                action = UiValueAction(::moreFromArtist),
                appliesTo = { artistKey(it) != null },
            ),
        ),
        accents = listOf(PluginAccent(ACCENT_ID, "Audius", ACCENT_ARGB)),
    )

    private fun trending(): Flow<List<UiNode>> = flow {
        emit(listOf(UiNode.Loading))
        val tracks = api.trending(TRENDING_LIMIT).filter { it.isStreamable }.map(AudiusSource::track)
        emit(if (tracks.isEmpty()) listOf(UiNode.Status("Nothing is trending right now.")) else listOf(UiNode.Tracks(tracks)))
    }

    private suspend fun moreFromArtist(track: Track): UiEffect {
        val key = artistKey(track) ?: return UiEffect.None
        val tracks = api.userTracks(key, 0, ARTIST_LIMIT).filter { it.isStreamable }.map(AudiusSource::track)
        return if (tracks.isEmpty()) UiEffect.Message("No other tracks from ${track.artistLine}") else UiEffect.Play(tracks)
    }

    private fun artistKey(track: Track): String? =
        track.artists.firstOrNull()?.id
            ?.takeIf { track.id.source == AudiusSource.SOURCE_ID && it.source == AudiusSource.SOURCE_ID }
            ?.key?.removePrefix(AudiusSource.ARTIST_PREFIX)

    companion object {
        const val TRENDING_ID: String = "trending"
        const val MORE_FROM_ARTIST_ID: String = "more-from-artist"
        const val ACCENT_ID: String = "audius"
        const val ACCENT_ARGB: Long = 0xFFCC0FE0
        private const val TRENDING_LIMIT = 10
        private const val ARTIST_LIMIT = 50
    }
}
