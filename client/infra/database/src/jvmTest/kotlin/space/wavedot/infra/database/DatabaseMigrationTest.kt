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
                statement.execute(
                    "CREATE TABLE favorite (source TEXT NOT NULL, media_key TEXT NOT NULL, title TEXT NOT NULL, " +
                        "artists TEXT NOT NULL, album TEXT, duration_ms INTEGER, artwork_url TEXT, " +
                        "added_at INTEGER NOT NULL, PRIMARY KEY (source, media_key))",
                )
                statement.execute(
                    "CREATE TABLE favorite_change (source TEXT NOT NULL, media_key TEXT NOT NULL, " +
                        "deleted INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (source, media_key))",
                )
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

    @Test
    fun replacesCredentialedSubsonicArtworkWithReferences() = runTest {
        val credentialed = "https://music.test/rest/getCoverArt.view?u=igor&t=abc&s=salt&v=1.16.1&c=Wavedot&id=al-1&size=480"
        createJvmDatabase(file)
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO favorite(source, media_key, title, artists, artwork_url, added_at) " +
                        "VALUES ('subsonic', 's1', 'Song', '', '$credentialed', 1), " +
                        "('audius', 'a1', 'Other', '', 'https://cdn.test/a.jpg', 1)",
                )
                statement.execute("INSERT INTO playlist(id, title, created_at, updated_at) VALUES ('p', 'Mix', 1, 1)")
                statement.execute(
                    "INSERT INTO playlist_track(playlist_id, media_id, position, title, artists, artwork_url, added_at) " +
                        "VALUES ('p', 'subsonic:s1', 0, 'Song', '', '$credentialed', 1)",
                )
                statement.execute("PRAGMA user_version = 3")
            }
        }

        val favorites = SqlFavoritesRepository(createJvmDatabase(file), Dispatchers.Unconfined)

        val stored = favorites.observeAll().first().associate { it.track.id.toString() to it.track.artwork?.url }
        assertEquals("wavedot-artwork:subsonic:al-1", stored["subsonic:s1"])
        assertEquals("https://cdn.test/a.jpg", stored["audius:a1"])
        assertEquals(listOf("subsonic:s1"), favorites.pendingChanges().map { it.id.toString() })
        assertEquals(
            "wavedot-artwork:subsonic:al-1",
            query("SELECT artwork_url FROM playlist_track WHERE media_id = 'subsonic:s1'"),
        )
        assertEquals(WavedotDatabase.Schema.version, userVersion())
    }

    private fun query(sql: String): String? =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { it.executeQuery(sql).getString(1) }
        }

    private fun userVersion(): Long =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { it.executeQuery("PRAGMA user_version").getLong(1) }
        }
}
