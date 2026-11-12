package space.wavedot.server.updates.data

import kotlinx.coroutines.test.runTest
import space.wavedot.server.testing.MutableClock
import space.wavedot.server.testing.TestDatabase
import space.wavedot.server.updates.domain.Arch
import space.wavedot.server.updates.domain.ArtifactLocation
import space.wavedot.server.updates.domain.ReleaseCatalog
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatesDataTest {
    private val db = TestDatabase("classpath:db/migration/updates")
    private val repository = ExposedReleaseRepository(db.database)
    private val storage = FileSystemArtifactStorage(Files.createTempDirectory("artifacts"))

    @Test
    fun publishesAndReadsBackReleases() = runTest {
        val clock = MutableClock()
        val catalog = ReleaseCatalog(repository, clock)
        val publish = PublishRelease(repository, storage, { "rel-1" }, clock, catalog)
        publish(
            PublishCommand(
                "1.2.3-beta.1", Channel.BETA, "Notes", true, 50,
                listOf(
                    ArtifactUpload(Platform.WINDOWS, Arch.X64, "wavedot.msi", "msi".byteInputStream(), "c2lnbmF0dXJl"),
                    ArtifactUpload(Platform.ANDROID, Arch.UNIVERSAL, "wavedot.apk", "apk".byteInputStream()),
                ),
            ),
        )
        val loaded = repository.findByVersion(SemVer.parse("1.2.3-beta.1"))!!
        assertEquals(2, loaded.artifacts.size)
        assertEquals(50, loaded.rolloutPercent)
        val windows = loaded.artifactFor(Platform.WINDOWS, Arch.X64)!!
        assertEquals("c2lnbmF0dXJl", windows.signature)
        assertNull(loaded.artifactFor(Platform.ANDROID, Arch.UNIVERSAL)!!.signature)
        val location = assertIs<ArtifactLocation.LocalFile>(storage.locate(windows.storageKey))
        assertEquals("msi", Files.readString(location.path))
        assertNull(storage.locate("rel-1/missing.bin"))

        YankRelease(repository, catalog)("rel-1")
        assertTrue(repository.listActive().isEmpty())
    }

    @Test
    fun storageRejectsPathTraversal() = runTest {
        assertFailsWith<IllegalArgumentException> { storage.locate("../outside") }
        assertFailsWith<IllegalArgumentException> { storage.store("../outside", "x".byteInputStream()) }
        assertFailsWith<IllegalArgumentException> { storage.store("rel/..", "x".byteInputStream()) }
    }
}
