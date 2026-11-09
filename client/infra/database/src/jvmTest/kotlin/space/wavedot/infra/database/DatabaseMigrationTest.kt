package space.wavedot.infra.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import space.wavedot.core.model.lyrics.Lyrics
import space.wavedot.core.testing.TestTracks
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseMigrationTest {
    private val directory = Files.createTempDirectory("wavedot-db").toFile()
    private val file = File(directory, "wavedot.db")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun upgradesVersionOneDatabase() = runTest {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE favorite (id TEXT NOT NULL PRIMARY KEY)")
                statement.execute("PRAGMA user_version = 1")
            }
        }

        val cache = SqlLyricsCache(createJvmDatabase(file), Dispatchers.Unconfined)
        val id = TestTracks.track("1").id
        val lyrics = Lyrics.plain("Line", "LRCLIB")
        cache.put(id, lyrics, 1)

        assertEquals(lyrics, cache.get(id)?.lyrics)

        val playlists = SqlPlaylistRepository(createJvmDatabase(file), Dispatchers.Unconfined)
        playlists.create("p", "Mix", 1)
        playlists.add("p", listOf(TestTracks.track("1")), 2)
        assertEquals(1, playlists.observeAll().first().single().trackCount)
        assertEquals(WavedotDatabase.Schema.version, userVersion())
    }

    private fun userVersion(): Long =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { it.executeQuery("PRAGMA user_version").getLong(1) }
        }
}
