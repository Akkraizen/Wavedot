package space.wavedot.core.domain.library

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.WavedotException
import space.wavedot.core.testing.InMemoryPlaylistRepository
import space.wavedot.core.testing.TestTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistEditorTest {
    private val repository = InMemoryPlaylistRepository()
    private var now = 100L
    private var nextId = 0
    private val editor = PlaylistEditor(repository, { now }, { "p${++nextId}" })

    @Test
    fun createsPlaylistWithNormalizedTitleAndUniqueTracks() = runTest {
        val tracks = TestTracks.tracks(2)
        val id = editor.create("  Road   trip ", tracks + tracks.first())

        val details = repository.observe(id).first()!!
        assertEquals("Road trip", details.summary.title)
        assertEquals(tracks, details.tracks)
    }

    @Test
    fun blankTitleFallsBackToDefault() = runTest {
        val id = editor.create("   ")
        assertEquals(PlaylistEditor.DEFAULT_TITLE, repository.observe(id).first()!!.summary.title)
    }

    @Test
    fun addSkipsTracksAlreadyInPlaylist() = runTest {
        val tracks = TestTracks.tracks(3)
        val id = editor.create("Mix", tracks.take(1))

        assertEquals(2, editor.add(id, tracks))
        assertEquals(0, editor.add(id, tracks))
        assertEquals(tracks, repository.observe(id).first()!!.tracks)
    }

    @Test
    fun toggleAddsMissingTracksThenRemovesAll() = runTest {
        val tracks = TestTracks.tracks(2)
        val id = editor.create("Mix", tracks.take(1))

        assertTrue(editor.toggle(id, tracks))
        assertEquals(tracks, repository.observe(id).first()!!.tracks)

        assertFalse(editor.toggle(id, tracks))
        assertEquals(emptyList(), repository.observe(id).first()!!.tracks)
    }

    @Test
    fun renameKeepsTitleWhenBlankAndUpdatesTimestamp() = runTest {
        val id = editor.create("Mix")
        now = 200
        editor.rename(id, " ")
        assertEquals("Mix", repository.observe(id).first()!!.summary.title)

        editor.rename(id, "Evening")
        val summary = repository.observe(id).first()!!.summary
        assertEquals("Evening", summary.title)
        assertEquals(200, summary.updatedAt)
    }

    @Test
    fun deleteRemovesPlaylist() = runTest {
        val id = editor.create("Mix")
        editor.delete(id)
        assertNull(repository.observe(id).first())
    }

    @Test
    fun addingToMissingPlaylistFails() = runTest {
        assertFailsWith<WavedotException> { editor.add("missing", TestTracks.tracks(1)) }
    }
}
