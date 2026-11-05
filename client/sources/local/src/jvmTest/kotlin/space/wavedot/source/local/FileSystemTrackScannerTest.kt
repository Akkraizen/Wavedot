package space.wavedot.source.local

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSystemTrackScannerTest {
    @Test
    fun findsSupportedFilesAndFallsBackToFileName() = runTest {
        val root = Files.createTempDirectory("music").toFile()
        root.resolve("album").mkdirs()
        root.resolve("album/first song.flac").writeBytes(byteArrayOf(0, 1, 2))
        root.resolve("notes.txt").writeText("ignore me")
        val tracks = FileSystemTrackScanner { listOf(root) }.scan()
        val track = tracks.single()
        assertEquals("first song", track.track.title)
        assertEquals("audio/flac", track.mimeType)
    }
}
