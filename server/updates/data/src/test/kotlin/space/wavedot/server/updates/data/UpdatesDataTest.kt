package space.wavedot.server.updates.data

import kotlinx.coroutines.test.runTest
import space.wavedot.server.testing.MutableClock
import space.wavedot.server.testing.TestDatabase
import space.wavedot.server.updates.domain.Arch
import space.wavedot.server.updates.domain.ArtifactUpload
import space.wavedot.server.updates.domain.Channel
import space.wavedot.server.updates.domain.Platform
import space.wavedot.server.updates.domain.PublishCommand
import space.wavedot.server.updates.domain.PublishRelease
import space.wavedot.server.updates.domain.SemVer
import space.wavedot.server.updates.domain.YankRelease
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UpdatesDataTest {
    private val db = TestDatabase("classpath:db/migration/updates")
    private val repository = ExposedReleaseRepository(db.database)
    private val storage = FileSystemArtifactStorage(Files.createTempDirectory("artifacts"))

    @Test
    fun publishesAndReadsBackReleases() = runTest {
        val publish = PublishRelease(repository, storage, { "rel-1" }, MutableClock())
        publish(
            PublishCommand(
                "1.2.3-beta.1", Channel.BETA, "Notes", true, 50,
                listOf(
                    ArtifactUpload(Platform.WINDOWS, Arch.X64, "wavedot.msi", "msi".byteInputStream()),
                    ArtifactUpload(Platform.ANDROID, Arch.UNIVERSAL, "wavedot.apk", "apk".byteInputStream()),
                ),
            ),
        )
        val loaded = repository.findByVersion(SemVer.parse("1.2.3-beta.1"))!!
        assertEquals(2, loaded.artifacts.size)
        assertEquals(50, loaded.rolloutPercent)
        assertEquals("msi", Files.readString(storage.resolve(loaded.artifactFor(Platform.WINDOWS, Arch.X64)!!.storageKey)))

        YankRelease(repository)("rel-1")
        assertTrue(repository.listActive().isEmpty())
    }

    @Test
    fun storageRejectsPathTraversal() {
        assertFailsWith<IllegalArgumentException> { storage.resolve("../outside") }
    }
}
