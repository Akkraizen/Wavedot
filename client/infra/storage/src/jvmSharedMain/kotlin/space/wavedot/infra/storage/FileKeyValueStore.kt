package space.wavedot.infra.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import space.wavedot.core.common.KeyValueStore
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties

class FileKeyValueStore(
    private val file: File,
    private val ownerOnly: Boolean = false,
) : KeyValueStore {
    private val mutex = Mutex()
    private val state = MutableStateFlow(readFile().also { if (ownerOnly && file.exists()) restrictToOwner(file) })

    override suspend fun get(key: String): String? = state.value[key]

    override suspend fun set(key: String, value: String) = write { it + (key to value) }

    override suspend fun delete(key: String) = write { it - key }

    override fun observe(key: String): Flow<String?> =
        state
            .map { it[key] }
            .distinctUntilChanged()

    private suspend fun write(
        transform: (Map<String, String>) -> Map<String, String>,
    ) = mutex.withLock {
        val updated = transform(state.value)
        if (updated == state.value) {
            return@withLock
        }
        withContext(Dispatchers.IO) { writeFile(updated) }
        state.value = updated
    }

    private fun readFile(): Map<String, String> {
        if (!file.exists()) {
            return emptyMap()
        }
        val properties = Properties()
        file.inputStream().use(properties::load)
        return properties.stringPropertyNames().associateWith(properties::getProperty)
    }

    private fun writeFile(values: Map<String, String>) {
        file.parentFile?.mkdirs()
        val properties = Properties()
        values.forEach { (k, v) -> properties.setProperty(k, v) }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.delete()
        temp.createNewFile()
        if (ownerOnly) restrictToOwner(temp)
        temp.outputStream().use { properties.store(it, null) }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun restrictToOwner(target: File) {
        val path = target.toPath()
        if ("posix" in path.fileSystem.supportedFileAttributeViews()) {
            Files.setPosixFilePermissions(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        } else {
            target.setReadable(false, false)
            target.setReadable(true, true)
            target.setWritable(false, false)
            target.setWritable(true, true)
        }
    }
}
