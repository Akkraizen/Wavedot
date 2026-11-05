package space.wavedot.server.updates.api

import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.updates.ArtifactDto
import space.wavedot.protocol.updates.ChannelDto
import space.wavedot.protocol.updates.PublishReleaseRequest
import space.wavedot.protocol.updates.PublishedReleaseDto
import space.wavedot.protocol.updates.ReleaseDto
import space.wavedot.protocol.updates.UpdatesRoutes
import space.wavedot.server.kernel.AppError
import space.wavedot.server.ktor.bearerToken
import space.wavedot.server.updates.domain.Arch
import space.wavedot.server.updates.domain.ArtifactUpload
import space.wavedot.server.updates.domain.Channel
import space.wavedot.server.updates.domain.Platform
import space.wavedot.server.updates.domain.PublishCommand
import space.wavedot.server.updates.domain.PublishRelease
import space.wavedot.server.updates.domain.ReleaseRepository
import space.wavedot.server.updates.domain.ResolveLatest
import space.wavedot.server.updates.domain.SemVer
import space.wavedot.server.updates.domain.UpdateQuery
import space.wavedot.server.updates.domain.YankRelease
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

data class AdminPrincipal(val name: String)

class UpdatesUseCases(
    val resolveLatest: ResolveLatest,
    val publish: PublishRelease,
    val yank: YankRelease,
    val releases: ReleaseRepository,
    val artifactFile: (String) -> File,
    val adminToken: String,
)

const val ADMIN_AUTH = "admin"

fun Application.updatesApi(useCases: UpdatesUseCases) {
    install(PartialContent)
    install(AutoHeadResponse)
    install(Authentication) {
        bearerToken(ADMIN_AUTH) { token ->
            if (useCases.adminToken.isNotBlank() && constantTimeEquals(token, useCases.adminToken)) AdminPrincipal("admin") else null
        }
    }
    routing {
        publicRoutes(useCases)
        authenticate(ADMIN_AUTH) { adminRoutes(useCases) }
    }
}

private fun Route.publicRoutes(useCases: UpdatesUseCases) {
    get(UpdatesRoutes.LATEST) {
        val params = call.request.queryParameters
        val query = UpdateQuery(
            platform = decode<PlatformDto>(params.require(UpdatesRoutes.PARAM_PLATFORM)).toDomain(),
            arch = decode<ArchDto>(params[UpdatesRoutes.PARAM_ARCH] ?: "universal").toDomain(),
            channel = decode<ChannelDto>(params[UpdatesRoutes.PARAM_CHANNEL] ?: "stable").toDomain(),
            current = SemVer.parse(params.require(UpdatesRoutes.PARAM_CURRENT)),
            installId = params[UpdatesRoutes.PARAM_INSTALL_ID].orEmpty(),
        )
        val resolved = useCases.resolveLatest(query) ?: return@get call.respond(HttpStatusCode.NoContent)
        val release = resolved.release
        val artifact = resolved.artifact
        call.respond(
            ReleaseDto(
                id = release.id,
                version = release.version.toString(),
                channel = release.channel.toDto(),
                publishedAt = release.publishedAt.toEpochMilli(),
                notes = release.notes,
                mandatory = release.mandatory,
                artifact = ArtifactDto(
                    platform = artifact.platform.toDto(),
                    arch = artifact.arch.toDto(),
                    fileName = artifact.fileName,
                    size = artifact.size,
                    sha256 = artifact.sha256,
                    url = "${UpdatesRoutes.ARTIFACTS}/${release.id}/${artifact.fileName}",
                ),
            ),
        )
    }
    get("${UpdatesRoutes.ARTIFACTS}/{releaseId}/{fileName}") {
        val releaseId = call.parameters["releaseId"]!!
        val fileName = call.parameters["fileName"]!!
        val release = useCases.releases.findById(releaseId) ?: throw AppError.NotFound("Release")
        val artifact = release.artifacts.firstOrNull { it.fileName == fileName } ?: throw AppError.NotFound("Artifact")
        val file = useCases.artifactFile(artifact.storageKey)
        if (!file.isFile) throw AppError.NotFound("Artifact file")
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, artifact.fileName).toString(),
        )
        call.respondFile(file)
    }
}

private fun Route.adminRoutes(useCases: UpdatesUseCases) {
    post(UpdatesRoutes.ADMIN_RELEASES) {
        var metadata: PublishReleaseRequest? = null
        val uploads = mutableListOf<ArtifactUpload>()
        val tempFiles = mutableListOf<File>()
        try {
            call.receiveMultipart(formFieldLimit = MAX_UPLOAD_BYTES).forEachPart { part ->
                when {
                    part is PartData.FormItem && part.name == "metadata" ->
                        metadata = ProtocolJson.decodeFromString<PublishReleaseRequest>(part.value)
                    part is PartData.FileItem && part.name?.startsWith("artifact:") == true -> {
                        val (_, platform, arch) = part.name!!.split(':').also { if (it.size != 3) throw AppError.Validation("Use artifact:<platform>:<arch>") }
                        val temp = Files.createTempFile("upload", ".bin").toFile().also(tempFiles::add)
                        part.provider().toInputStream().use { input -> temp.outputStream().use(input::copyTo) }
                        uploads += ArtifactUpload(
                            platform = decode<PlatformDto>(platform).toDomain(),
                            arch = decode<ArchDto>(arch).toDomain(),
                            fileName = part.originalFileName ?: throw AppError.Validation("File name is required"),
                            content = temp.inputStream(),
                        )
                    }
                }
                part.dispose()
            }
            val request = metadata ?: throw AppError.Validation("Missing metadata part")
            val release = useCases.publish(
                PublishCommand(request.version, request.channel.toDomain(), request.notes, request.mandatory, request.rolloutPercent, uploads),
            )
            call.respond(
                HttpStatusCode.Created,
                PublishedReleaseDto(release.id, release.version.toString(), release.channel.toDto(), release.artifacts.map { it.fileName }),
            )
        } finally {
            uploads.forEach { runCatching { it.content.close() } }
            tempFiles.forEach(File::delete)
        }
    }
    post("${UpdatesRoutes.ADMIN_RELEASES}/{id}/yank") {
        useCases.yank(call.parameters["id"]!!)
        call.respond(HttpStatusCode.NoContent)
    }
}

private const val MAX_UPLOAD_BYTES = 1024L * 1024 * 1024

private fun io.ktor.http.Parameters.require(name: String): String =
    this[name] ?: throw AppError.Validation("Missing query parameter '$name'")

private inline fun <reified T> decode(wire: String): T =
    runCatching { ProtocolJson.decodeFromString<T>("\"$wire\"") }.getOrElse { throw AppError.Validation("Unknown value '$wire'") }

private fun PlatformDto.toDomain() = Platform.valueOf(name)
private fun ArchDto.toDomain() = Arch.valueOf(name)
private fun ChannelDto.toDomain() = Channel.valueOf(name)
private fun Platform.toDto() = PlatformDto.valueOf(name)
private fun Arch.toDto() = ArchDto.valueOf(name)
private fun Channel.toDto() = ChannelDto.valueOf(name)

private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
