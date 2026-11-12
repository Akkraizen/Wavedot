package space.wavedot.infra.storage

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileKeyValueStoreTest {
    @Test
    fun persistsValuesAcrossInstances() = runTest {
        val file = Files.createTempDirectory("kv").resolve("settings.properties").toFile()
        val store = FileKeyValueStore(file)
        store.set("a", "1")
        store.set("b", "2")
        store.delete("b")
        val reopened = FileKeyValueStore(file)
        assertEquals("1", reopened.get("a"))
        assertNull(reopened.get("b"))
    }

    @Test
    fun ownerOnlyStoresAreUnreadableForOthers() = runTest {
        val file = Files.createTempDirectory("kv").resolve("secrets.properties").toFile()
        if ("posix" !in file.toPath().fileSystem.supportedFileAttributeViews()) return@runTest
        file.writeText("legacy=1\n")
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-r--r--"))

        val store = FileKeyValueStore(file, ownerOnly = true)
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))

        store.set("token", "secret")
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
        assertEquals("1", FileKeyValueStore(file, ownerOnly = true).get("legacy"))
    }
}
