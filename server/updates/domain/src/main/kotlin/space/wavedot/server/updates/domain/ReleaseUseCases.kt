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

class ResolveLatest(private val catalog: ReleaseCatalog) {
    suspend operator fun invoke(query: UpdateQuery): ResolvedUpdate? {
        validate(query.installId.length <= MAX_INSTALL_ID_LENGTH) { "Install id must be at most $MAX_INSTALL_ID_LENGTH characters" }
        return catalog.active()
            .asSequence()
            .filter { !it.yanked && query.channel.accepts(it.channel) && it.version > query.current }
            .filter { inRollout(it, query.installId) }
            .mapNotNull { release -> release.artifactFor(query.platform, query.arch)?.let { ResolvedUpdate(release, it) } }
            .maxByOrNull { it.release.version }
    }

    private fun inRollout(release: Release, installId: String): Boolean {
        if (release.rolloutPercent >= 100) return true
        if (release.rolloutPercent <= 0) return false
        return bucket("${release.id}:$installId") < release.rolloutPercent
    }

    internal companion object {
        const val MAX_INSTALL_ID_LENGTH = 128

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
    val signature: String? = null,
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
    private val catalog: ReleaseCatalog,
    private val signatures: ArtifactSignatureVerifier? = null,
) {
    suspend operator fun invoke(command: PublishCommand): Release {
        val version = SemVer.parse(command.version)
        validate(command.rolloutPercent in 0..100) { "Rollout must be within 0..100" }
        validate(command.notes.length <= MAX_NOTES_LENGTH) { "Notes must be at most $MAX_NOTES_LENGTH characters" }
        validate(command.artifacts.isNotEmpty()) { "At least one artifact is required" }
        validate(command.artifacts.distinctBy { it.platform to it.arch }.size == command.artifacts.size) {
            "Only one artifact per platform and architecture"
        }
        command.artifacts.forEach { upload ->
            validate(FILE_NAME.matches(upload.fileName)) { "Invalid file name: ${upload.fileName}" }
            validate((upload.signature?.length ?: 0) <= MAX_SIGNATURE_LENGTH) { "Signature for ${upload.fileName} is too long" }
            validate(signatures == null || !upload.signature.isNullOrBlank()) { "A signature is required for ${upload.fileName}" }
        }
        if (releases.findByVersion(version) != null) throw AppError.Conflict("Version $version already exists")

        val id = ids.newId()
        val stored = mutableListOf<Artifact>()
        try {
            command.artifacts.forEach { upload ->
                val key = "$id/${upload.fileName}"
                val blob = storage.store(key, upload.content)
                val artifact = Artifact(upload.platform, upload.arch, upload.fileName, blob.size, blob.sha256, key, upload.signature?.trim())
                stored += artifact
                validate(signatures == null || signatures.isValid(version, artifact, artifact.signature.orEmpty())) {
                    "The signature for ${upload.fileName} does not match the uploaded file"
                }
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
            catalog.invalidate()
            return release
        } catch (t: Throwable) {
            stored.forEach { runCatching { storage.delete(it.storageKey) } }
            throw t
        }
    }

    private companion object {
        const val MAX_NOTES_LENGTH = 20_000
        const val MAX_SIGNATURE_LENGTH = 256
        val FILE_NAME = Regex("""^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$""")
    }
}

class YankRelease(
    private val releases: ReleaseRepository,
    private val catalog: ReleaseCatalog,
) {
    suspend operator fun invoke(id: String) {
        releases.findById(id) ?: throw AppError.NotFound("Release $id")
        releases.markYanked(id)
        catalog.invalidate()
    }
}

data class ArtifactDownload(
    val artifact: Artifact,
    val location: ArtifactLocation,
)

class GetArtifact(
    private val releases: ReleaseRepository,
    private val storage: ArtifactStorage,
) {
    suspend operator fun invoke(releaseId: String, fileName: String): ArtifactDownload {
        val release = releases.findById(releaseId)?.takeUnless { it.yanked } ?: throw AppError.NotFound("Release")
        val artifact = release.artifacts.firstOrNull { it.fileName == fileName } ?: throw AppError.NotFound("Artifact")
        val location = storage.locate(artifact.storageKey) ?: throw AppError.NotFound("Artifact file")
        return ArtifactDownload(artifact, location)
    }
}
