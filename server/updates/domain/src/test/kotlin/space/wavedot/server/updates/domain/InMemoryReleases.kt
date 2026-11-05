package space.wavedot.server.updates.domain

import java.io.InputStream
import java.security.MessageDigest

class InMemoryReleases : ReleaseRepository, ArtifactStorage {
    val releases = linkedMapOf<String, Release>()
    val blobs = mutableMapOf<String, ByteArray>()

    override suspend fun save(release: Release) {
        releases[release.id] = release
    }

    override suspend fun findById(id: String): Release? = releases[id]

    override suspend fun findByVersion(version: SemVer): Release? = releases.values.firstOrNull { it.version == version }

    override suspend fun listActive(): List<Release> = releases.values.filter { !it.yanked }

    override suspend fun markYanked(id: String) {
        releases[id] = releases.getValue(id).copy(yanked = true)
    }

    override suspend fun store(key: String, content: InputStream): StoredBlob {
        val bytes = content.readBytes()
        blobs[key] = bytes
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return StoredBlob(bytes.size.toLong(), sha)
    }

    override suspend fun delete(key: String) {
        blobs.remove(key)
    }
}
