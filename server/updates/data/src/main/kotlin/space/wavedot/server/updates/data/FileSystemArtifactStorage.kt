package space.wavedot.server.updates.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.wavedot.server.updates.domain.ArtifactStorage
import space.wavedot.server.updates.domain.StoredBlob
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest

class FileSystemArtifactStorage(root: Path) : ArtifactStorage {
    private val root: Path = root.toAbsolutePath().normalize().also { Files.createDirectories(it) }

    fun resolve(key: String): Path {
        val path = root.resolve(key).normalize()
        require(path.startsWith(root)) { "Storage key escapes the root: $key" }
        return path
    }

    override suspend fun store(key: String, content: InputStream): StoredBlob = withContext(Dispatchers.IO) {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, ".upload", ".tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            DigestInputStream(content, digest).use { Files.copy(it, temp, StandardCopyOption.REPLACE_EXISTING) }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
        StoredBlob(Files.size(target), digest.digest().joinToString("") { "%02x".format(it) })
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { Files.deleteIfExists(resolve(key)) }
    }
}
