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
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.path
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.core.fromWireName
import space.wavedot.protocol.updates.ArtifactDto
import space.wavedot.protocol.updates.ChannelDto
import space.wavedot.protocol.updates.PublishReleaseRequest
import space.wavedot.protocol.updates.PublishedReleaseDto
import space.wavedot.protocol.updates.ReleaseDto
import space.wavedot.protocol.updates.UpdatesRoutes
import space.wavedot.server.kernel.AppError
import space.wavedot.server.ktor.bearerToken
import space.wavedot.server.ktor.perRemoteHost
import space.wavedot.server.updates.domain.ArtifactLocation
import space.wavedot.server.updates.domain.ArtifactUpload
import space.wavedot.server.updates.domain.GetArtifact
import space.wavedot.server.updates.domain.PublishCommand
import space.wavedot.server.updates.domain.PublishRelease
import space.wavedot.server.updates.domain.ResolveLatest
import space.wavedot.server.updates.domain.SemVer
import space.wavedot.server.updates.domain.UpdateQuery
import space.wavedot.server.updates.domain.YankRelease
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.time.Duration.Companion.minutes

data class AdminPrincipal(val name: String)

class UpdatesUseCases(
    val resolveLatest: ResolveLatest,
    val publish: PublishRelease,
    val yank: YankRelease,
    val getArtifact: GetArtifact,
    val adminToken: String,
)

data class UpdatesApiLimits(
    val publicRequestsPerMinute: Int = 120,
    val adminRequestsPerMinute: Int = 20,
    val maxUploadBytes: Long = 1024L * 1024 * 1024,
    val maxRequestBytes: Long = 64 * 1024,
)

const val ADMIN_AUTH = "admin"
const val MIN_ADMIN_TOKEN_LENGTH = 32

private val PUBLIC_LIMIT = RateLimitName("public")
private val ADMIN_LIMIT = RateLimitName("admin")

fun Application.updatesApi(useCases: UpdatesUseCases, limits: UpdatesApiLimits = UpdatesApiLimits()) {
    require(useCases.adminToken.isBlank() || useCases.adminToken.length >= MIN_ADMIN_TOKEN_LENGTH) {
        "The admin token must be at least $MIN_ADMIN_TOKEN_LENGTH characters long"
    }
    install(PartialContent)
    install(AutoHeadResponse)
    install(RequestBodyLimit) {
        bodyLimit { call ->
            if (call.request.path() == UpdatesRoutes.ADMIN_RELEASES) limits.maxUploadBytes else limits.maxRequestBytes
        }
    }
    install(RateLimit) {
        perRemoteHost(PUBLIC_LIMIT, limits.publicRequestsPerMinute, 1.minutes)
        perRemoteHost(ADMIN_LIMIT, limits.adminRequestsPerMinute, 1.minutes)
    }
    install(Authentication) {
        bearerToken(ADMIN_AUTH) { token ->
            if (useCases.adminToken.isNotBlank() && constantTimeEquals(token, useCases.adminToken)) AdminPrincipal("admin") else null
        }
    }
    routing {
        rateLimit(PUBLIC_LIMIT) { publicRoutes(useCases) }
        rateLimit(ADMIN_LIMIT) {
            authenticate(ADMIN_AUTH) { adminRoutes(useCases, limits) }
        }
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
                    signature = artifact.signature,
                ),
            ),
        )
    }
    get("${UpdatesRoutes.ARTIFACTS}/{releaseId}/{fileName}") {
        val download = useCases.getArtifact(call.parameters["releaseId"]!!, call.parameters["fileName"]!!)
        when (val location = download.location) {
            is ArtifactLocation.Remote -> call.respondRedirect(location.url)
            is ArtifactLocation.LocalFile -> {
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, download.artifact.fileName)
                        .toString(),
                )
                call.respondFile(location.path.toFile())
            }
        }
    }
}

private class ReceivedUpload(
    val platform: String,
    val arch: String,
    val fileName: String,
    val file: File,
)

private fun Route.adminRoutes(useCases: UpdatesUseCases, limits: UpdatesApiLimits) {
    post(UpdatesRoutes.ADMIN_RELEASES) {
        var metadata: PublishReleaseRequest? = null
        val received = mutableListOf<ReceivedUpload>()
        val signatures = mutableMapOf<String, String>()
        val tempFiles = mutableListOf<File>()
        val streams = mutableListOf<java.io.InputStream>()
        try {
            call.receiveMultipart(formFieldLimit = limits.maxUploadBytes).forEachPart { part ->
                val name = part.name.orEmpty()
                when {
                    part is PartData.FormItem && name == METADATA_PART ->
                        metadata = ProtocolJson.decodeFromString<PublishReleaseRequest>(part.value)
                    part is PartData.FormItem && name.startsWith(SIGNATURE_PREFIX) ->
                        signatures[name.removePrefix(SIGNATURE_PREFIX)] = part.value
                    part is PartData.FileItem && name.startsWith(ARTIFACT_PREFIX) -> {
                        val target = name.removePrefix(ARTIFACT_PREFIX).split(':')
                        if (target.size != 2) throw AppError.Validation("Use artifact:<platform>:<arch>")
                        val fileName = part.originalFileName ?: throw AppError.Validation("File name is required")
                        val temp = withContext(Dispatchers.IO) {
                            Files.createTempFile("upload", ".bin").toFile().also(tempFiles::add).also { file ->
                                part.provider().toInputStream().use { input -> file.outputStream().use(input::copyTo) }
                            }
                        }
                        received += ReceivedUpload(target[0], target[1], fileName, temp)
                    }
                }
                part.dispose()
            }
            val request = metadata ?: throw AppError.Validation("Missing metadata part")
            val uploads = received.map { upload ->
                ArtifactUpload(
                    platform = decode<PlatformDto>(upload.platform).toDomain(),
                    arch = decode<ArchDto>(upload.arch).toDomain(),
                    fileName = upload.fileName,
                    content = upload.file.inputStream().also(streams::add),
                    signature = signatures["${upload.platform}:${upload.arch}"],
                )
            }
            val release = useCases.publish(
                PublishCommand(request.version, request.channel.toDomain(), request.notes, request.mandatory, request.rolloutPercent, uploads),
            )
            call.respond(
                HttpStatusCode.Created,
                PublishedReleaseDto(release.id, release.version.toString(), release.channel.toDto(), release.artifacts.map { it.fileName }),
            )
        } finally {
            streams.forEach { runCatching { it.close() } }
            tempFiles.forEach(File::delete)
        }
    }
    post("${UpdatesRoutes.ADMIN_RELEASES}/{id}/yank") {
        useCases.yank(call.parameters["id"]!!)
        call.respond(HttpStatusCode.NoContent)
    }
}

private const val METADATA_PART = "metadata"
private const val ARTIFACT_PREFIX = "artifact:"
private const val SIGNATURE_PREFIX = "signature:"

private fun io.ktor.http.Parameters.require(name: String): String =
    this[name] ?: throw AppError.Validation("Missing query parameter '$name'")

private inline fun <reified T> decode(wire: String): T =
    fromWireName<T>(wire) ?: throw AppError.Validation("Unknown value '${wire.take(32)}'")

private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
