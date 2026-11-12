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
        root.resolve("album/cover.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val tracks = FileSystemTrackScanner { listOf(root) }.scan()
        val track = tracks.single()
        assertEquals("first song", track.track.title)
        assertEquals("audio/flac", track.mimeType)
        assertEquals(root.resolve("album/cover.jpg").absolutePath, track.track.artwork?.url)
    }

    @Test
    fun followsLinkedFoldersWithoutLoopingForever() = runTest {
        val root = Files.createTempDirectory("music").toFile()
        val shelf = Files.createTempDirectory("shelf").toFile()
        shelf.resolve("linked.mp3").writeBytes(byteArrayOf(0))
        root.resolve("a").mkdirs()
        root.resolve("a/song.mp3").writeBytes(byteArrayOf(0))
        val linked = runCatching {
            Files.createSymbolicLink(root.resolve("shelf").toPath(), shelf.toPath())
            Files.createSymbolicLink(root.resolve("a/loop").toPath(), root.toPath())
        }.isSuccess
        if (!linked) return@runTest

        val titles = FileSystemTrackScanner { listOf(root) }.scan().map { it.track.title }.sorted()

        assertEquals(listOf("linked", "song"), titles)
    }
}
