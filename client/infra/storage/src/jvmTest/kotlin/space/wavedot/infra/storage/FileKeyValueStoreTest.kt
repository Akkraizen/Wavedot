package space.wavedot.infra.storage

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
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
}
