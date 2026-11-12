package space.wavedot.server.updates.data

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import space.wavedot.server.persistence.dbQuery
import space.wavedot.server.updates.domain.Arch
import space.wavedot.server.updates.domain.Artifact
import space.wavedot.server.updates.domain.Channel
import space.wavedot.server.updates.domain.Platform
import space.wavedot.server.updates.domain.Release
import space.wavedot.server.updates.domain.ReleaseRepository
import space.wavedot.server.updates.domain.SemVer
import java.time.Instant

internal object ReleasesTable : Table("releases") {
    val id = varchar("id", 64)
    val version = varchar("version", 64)
    val channel = varchar("channel", 16)
    val notes = text("notes")
    val mandatory = bool("mandatory")
    val rolloutPercent = integer("rollout_percent")
    val publishedAt = long("published_at")
    val yanked = bool("yanked")
    override val primaryKey = PrimaryKey(id)
}

internal object ArtifactsTable : Table("artifacts") {
    val releaseId = varchar("release_id", 64)
    val platform = varchar("platform", 16)
    val arch = varchar("arch", 16)
    val fileName = varchar("file_name", 128)
    val size = long("size")
    val sha256 = varchar("sha256", 64)
    val storageKey = varchar("storage_key", 256)
    val signature = varchar("signature", 256).nullable()
    override val primaryKey = PrimaryKey(releaseId, platform, arch)
}

class ExposedReleaseRepository(private val database: Database) : ReleaseRepository {
    override suspend fun save(release: Release) = database.dbQuery {
        ReleasesTable.insert {
            it[id] = release.id
            it[version] = release.version.toString()
            it[channel] = release.channel.name
            it[notes] = release.notes
            it[mandatory] = release.mandatory
            it[rolloutPercent] = release.rolloutPercent
            it[publishedAt] = release.publishedAt.toEpochMilli()
            it[yanked] = release.yanked
        }
        ArtifactsTable.batchInsert(release.artifacts) { artifact ->
            this[ArtifactsTable.releaseId] = release.id
            this[ArtifactsTable.platform] = artifact.platform.name
            this[ArtifactsTable.arch] = artifact.arch.name
            this[ArtifactsTable.fileName] = artifact.fileName
            this[ArtifactsTable.size] = artifact.size
            this[ArtifactsTable.sha256] = artifact.sha256
            this[ArtifactsTable.storageKey] = artifact.storageKey
            this[ArtifactsTable.signature] = artifact.signature
        }
        Unit
    }

    override suspend fun findById(id: String): Release? = database.dbQuery {
        load(ReleasesTable.selectAll().where { ReleasesTable.id eq id }.toList()).singleOrNull()
    }

    override suspend fun findByVersion(version: SemVer): Release? = database.dbQuery {
        load(ReleasesTable.selectAll().where { ReleasesTable.version eq version.toString() }.toList()).singleOrNull()
    }

    override suspend fun listActive(): List<Release> = database.dbQuery {
        load(ReleasesTable.selectAll().where { ReleasesTable.yanked eq false }.toList())
    }

    override suspend fun markYanked(id: String) = database.dbQuery {
        ReleasesTable.update({ ReleasesTable.id eq id }) { it[yanked] = true }
        Unit
    }

    private fun load(rows: List<ResultRow>): List<Release> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it[ReleasesTable.id] }
        val artifacts = ArtifactsTable.selectAll().where { ArtifactsTable.releaseId inList ids }
            .groupBy({ it[ArtifactsTable.releaseId] }) { it.toArtifact() }
        return rows.map { row ->
            Release(
                id = row[ReleasesTable.id],
                version = SemVer.parse(row[ReleasesTable.version]),
                channel = Channel.valueOf(row[ReleasesTable.channel]),
                notes = row[ReleasesTable.notes],
                mandatory = row[ReleasesTable.mandatory],
                rolloutPercent = row[ReleasesTable.rolloutPercent],
                publishedAt = Instant.ofEpochMilli(row[ReleasesTable.publishedAt]),
                yanked = row[ReleasesTable.yanked],
                artifacts = artifacts[row[ReleasesTable.id]].orEmpty(),
            )
        }
    }

    private fun ResultRow.toArtifact() = Artifact(
        platform = Platform.valueOf(this[ArtifactsTable.platform]),
        arch = Arch.valueOf(this[ArtifactsTable.arch]),
        fileName = this[ArtifactsTable.fileName],
        size = this[ArtifactsTable.size],
        sha256 = this[ArtifactsTable.sha256],
        storageKey = this[ArtifactsTable.storageKey],
        signature = this[ArtifactsTable.signature],
    )
}
