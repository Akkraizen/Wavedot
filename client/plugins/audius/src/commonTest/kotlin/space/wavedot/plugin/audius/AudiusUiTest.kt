package space.wavedot.plugin.audius

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import space.wavedot.core.contract.ui.UiEffect
import space.wavedot.core.contract.ui.UiNode
import space.wavedot.core.model.MediaId
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudiusUiTest {
    private val server = FakeAudius()
    private val ui = AudiusUi(server.api).build()

    @Test
    fun trendingSectionListsStreamableTracks() = runTest {
        val emissions = ui.librarySections.single().content.toList()
        assertEquals(listOf(UiNode.Loading), emissions.first())
        val tracks = assertIs<UiNode.Tracks>(emissions.last().single()).tracks
        assertEquals(listOf("t3", "t1"), tracks.map { it.id.key })
    }

    @Test
    fun moreFromArtistPlaysTheArtistsTracks() = runTest {
        val action = ui.trackActions.single()
        val audiusTrack = assertNotNull(server.source().catalog!!.tracks.get(MediaId(AudiusSource.SOURCE_ID, "t1")))
        assertTrue(action.appliesTo(audiusTrack))
        assertFalse(action.appliesTo(TestTracks.track("1")))

        val effect = assertIs<UiEffect.Play>(action.action.run(audiusTrack))
        assertEquals(listOf("t3"), effect.tracks.map { it.id.key })
    }

    @Test
    fun contributesAnAccent() {
        assertEquals(AudiusUi.ACCENT_ARGB, ui.accents.single().argb)
    }
}
