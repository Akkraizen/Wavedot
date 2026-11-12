package space.wavedot.server.updates.domain

import java.io.InputStream
import java.nio.file.Path
import java.time.Instant

enum class Channel(val stability: Int) {
    STABLE(0),
    BETA(1),
    NIGHTLY(2),
    ;

    fun accepts(release: Channel): Boolean = release.stability <= stability
}

enum class Platform { ANDROID, WINDOWS, LINUX, MACOS }

enum class Arch { X64, ARM64, UNIVERSAL }

data class Artifact(
    val platform: Platform,
    val arch: Arch,
    val fileName: String,
    val size: Long,
    val sha256: String,
    val storageKey: String,
    val signature: String? = null,
)

data class Release(
    val id: String,
    val version: SemVer,
    val channel: Channel,
    val notes: String,
    val mandatory: Boolean,
    val rolloutPercent: Int,
    val publishedAt: Instant,
    val yanked: Boolean,
    val artifacts: List<Artifact>,
) {
    fun artifactFor(platform: Platform, arch: Arch): Artifact? =
        artifacts.firstOrNull { it.platform == platform && it.arch == arch }
            ?: artifacts.firstOrNull { it.platform == platform && it.arch == Arch.UNIVERSAL }
}

data class StoredBlob(
    val size: Long,
    val sha256: String,
)

sealed interface ArtifactLocation {
    data class LocalFile(val path: Path) : ArtifactLocation

    data class Remote(val url: String) : ArtifactLocation
}

interface ReleaseRepository {
    suspend fun save(release: Release)

    suspend fun findById(id: String): Release?

    suspend fun findByVersion(version: SemVer): Release?

    suspend fun listActive(): List<Release>

    suspend fun markYanked(id: String)
}

interface ArtifactStorage {
    suspend fun store(key: String, content: InputStream): StoredBlob

    suspend fun locate(key: String): ArtifactLocation?

    suspend fun delete(key: String)
}

fun interface ArtifactSignatureVerifier {
    fun isValid(version: SemVer, artifact: Artifact, signature: String): Boolean
}
