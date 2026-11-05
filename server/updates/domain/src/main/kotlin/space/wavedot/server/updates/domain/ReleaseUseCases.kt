package space.wavedot.server.updates.domain

import space.wavedot.server.kernel.AppError
import space.wavedot.server.kernel.IdGenerator
import space.wavedot.server.kernel.validate
import java.io.InputStream
import java.security.MessageDigest
import java.time.Clock

data class UpdateQuery(
    val platform: Platform,
    val arch: Arch,
    val channel: Channel,
    val current: SemVer,
    val installId: String,
)

data class ResolvedUpdate(
    val release: Release,
    val artifact: Artifact,
)

class ResolveLatest(private val releases: ReleaseRepository) {
    suspend operator fun invoke(query: UpdateQuery): ResolvedUpdate? =
        releases.listActive()
            .asSequence()
            .filter { !it.yanked && query.channel.accepts(it.channel) && it.version > query.current }
            .filter { inRollout(it, query.installId) }
            .mapNotNull { release -> release.artifactFor(query.platform, query.arch)?.let { ResolvedUpdate(release, it) } }
            .maxByOrNull { it.release.version }

    private fun inRollout(release: Release, installId: String): Boolean {
        if (release.rolloutPercent >= 100) return true
        if (release.rolloutPercent <= 0) return false
        return bucket("${release.id}:$installId") < release.rolloutPercent
    }

    internal companion object {
        fun bucket(seed: String): Int {
            val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
            val value = ((digest[0].toInt() and 0xFF) shl 8) or (digest[1].toInt() and 0xFF)
            return value % 100
        }
    }
}

data class ArtifactUpload(
    val platform: Platform,
    val arch: Arch,
    val fileName: String,
    val content: InputStream,
)

data class PublishCommand(
    val version: String,
    val channel: Channel,
    val notes: String,
    val mandatory: Boolean,
    val rolloutPercent: Int,
    val artifacts: List<ArtifactUpload>,
)

class PublishRelease(
    private val releases: ReleaseRepository,
    private val storage: ArtifactStorage,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(command: PublishCommand): Release {
        val version = SemVer.parse(command.version)
        validate(command.rolloutPercent in 0..100) { "Rollout must be within 0..100" }
        validate(command.artifacts.isNotEmpty()) { "At least one artifact is required" }
        validate(command.artifacts.distinctBy { it.platform to it.arch }.size == command.artifacts.size) {
            "Only one artifact per platform and architecture"
        }
        command.artifacts.forEach { validate(FILE_NAME.matches(it.fileName)) { "Invalid file name: ${it.fileName}" } }
        if (releases.findByVersion(version) != null) throw AppError.Conflict("Version $version already exists")

        val id = ids.newId()
        val stored = mutableListOf<Artifact>()
        try {
            command.artifacts.forEach { upload ->
                val key = "$id/${upload.fileName}"
                val blob = storage.store(key, upload.content)
                stored += Artifact(upload.platform, upload.arch, upload.fileName, blob.size, blob.sha256, key)
            }
            val release = Release(
                id = id,
                version = version,
                channel = command.channel,
                notes = command.notes,
                mandatory = command.mandatory,
                rolloutPercent = command.rolloutPercent,
                publishedAt = clock.instant(),
                yanked = false,
                artifacts = stored,
            )
            releases.save(release)
            return release
        } catch (t: Throwable) {
            stored.forEach { runCatching { storage.delete(it.storageKey) } }
            throw t
        }
    }

    private companion object {
        val FILE_NAME = Regex("""^[A-Za-z0-9._-]{1,128}$""")
    }
}

class YankRelease(private val releases: ReleaseRepository) {
    suspend operator fun invoke(id: String) {
        releases.findById(id) ?: throw AppError.NotFound("Release $id")
        releases.markYanked(id)
    }
}
