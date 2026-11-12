package space.wavedot.infra.updater

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import space.wavedot.core.common.SecureUrl
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.domain.update.AppInfo
import space.wavedot.core.domain.update.AvailableUpdate
import space.wavedot.core.domain.update.UpdateChannel
import space.wavedot.core.domain.update.UpdateChecker
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.core.ProtocolVersion
import space.wavedot.protocol.core.wireName
import space.wavedot.protocol.updates.ArtifactDto
import space.wavedot.protocol.updates.ChannelDto
import space.wavedot.protocol.updates.ReleaseDto
import space.wavedot.protocol.updates.UpdateManifest
import space.wavedot.protocol.updates.UpdatesRoutes

data class UpdateTarget(val platform: PlatformDto, val arch: ArchDto)

class HttpUpdateChecker(
    private val http: HttpClient,
    private val baseUrl: suspend () -> String?,
    private val target: UpdateTarget,
    private val signatures: UpdateSignatureVerifier,
) : UpdateChecker {
    override suspend fun latest(app: AppInfo): AvailableUpdate? {
        val configured = baseUrl()?.takeIf { it.isNotBlank() } ?: return null
        val base = SecureUrl.require(configured, "The update server URL").trimEnd('/')
        val response = http.get(base + UpdatesRoutes.LATEST) {
            header(ProtocolVersion.HEADER, ProtocolVersion.CURRENT)
            parameter(UpdatesRoutes.PARAM_PLATFORM, target.platform.wireName())
            parameter(UpdatesRoutes.PARAM_ARCH, target.arch.wireName())
            parameter(UpdatesRoutes.PARAM_CHANNEL, app.channel.toDto().wireName())
            parameter(UpdatesRoutes.PARAM_CURRENT, app.version)
            parameter(UpdatesRoutes.PARAM_INSTALL_ID, app.installId)
        }
        if (response.status == HttpStatusCode.NoContent) return null
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw WavedotException(WavedotError.Network("Update server responded ${response.status.value}"))
        }
        val release = runCatching { ProtocolJson.decodeFromString<ReleaseDto>(text) }.getOrElse {
            throw WavedotException(WavedotError.Network("Update server returned an unexpected response"), it)
        }
        val artifact = release.artifact
        requireTrusted(release, artifact)
        return AvailableUpdate(
            version = release.version,
            notes = release.notes,
            mandatory = release.mandatory,
            downloadUrl = downloadUrl(base, artifact.url),
            sha256 = artifact.sha256,
            size = artifact.size,
            fileName = artifact.fileName,
        )
    }

    private fun requireTrusted(release: ReleaseDto, artifact: ArtifactDto) {
        val described = artifact.platform == target.platform &&
            (artifact.arch == target.arch || artifact.arch == ArchDto.UNIVERSAL) &&
            artifact.size > 0 &&
            FILE_NAME.matches(artifact.fileName)
        val manifest = UpdateManifest.canonical(
            release.version, artifact.platform, artifact.arch, artifact.fileName, artifact.size, artifact.sha256,
        )
        val signature = artifact.signature
        if (!described || signature.isNullOrBlank() || !signatures.isTrusted(manifest, signature)) {
            throw WavedotException(WavedotError.Invalid("Version ${release.version.take(32)} is not signed by a trusted key"))
        }
    }

    private fun downloadUrl(base: String, url: String): String = when {
        url.startsWith("/") && !url.startsWith("//") -> base + url
        url.startsWith("$base/") -> url
        else -> throw WavedotException(WavedotError.Invalid("The update points to a different server"))
    }

    private fun UpdateChannel.toDto(): ChannelDto = when (this) {
        UpdateChannel.STABLE -> ChannelDto.STABLE
        UpdateChannel.BETA -> ChannelDto.BETA
        UpdateChannel.NIGHTLY -> ChannelDto.NIGHTLY
    }

    private companion object {
        val FILE_NAME = Regex("""^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$""")
    }
}
