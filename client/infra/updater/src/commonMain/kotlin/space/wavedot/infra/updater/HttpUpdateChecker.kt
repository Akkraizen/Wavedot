package space.wavedot.infra.updater

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.domain.update.AppInfo
import space.wavedot.core.domain.update.AvailableUpdate
import space.wavedot.core.domain.update.UpdateChannel
import space.wavedot.core.domain.update.UpdateChecker
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.updates.ChannelDto
import space.wavedot.protocol.updates.ReleaseDto
import space.wavedot.protocol.updates.UpdatesRoutes

data class UpdateTarget(val platform: PlatformDto, val arch: ArchDto)

class HttpUpdateChecker(
    private val http: HttpClient,
    private val baseUrl: suspend () -> String?,
    private val target: UpdateTarget,
) : UpdateChecker {
    override suspend fun latest(app: AppInfo): AvailableUpdate? {
        val base = baseUrl()?.trimEnd('/') ?: return null
        val response = http.get(base + UpdatesRoutes.LATEST) {
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
        val release = ProtocolJson.decodeFromString<ReleaseDto>(text)
        val url = release.artifact.url.let { if (it.startsWith("http")) it else base + it }
        return AvailableUpdate(
            version = release.version,
            notes = release.notes,
            mandatory = release.mandatory,
            downloadUrl = url,
            sha256 = release.artifact.sha256,
            size = release.artifact.size,
        )
    }

    private fun UpdateChannel.toDto(): ChannelDto = when (this) {
        UpdateChannel.STABLE -> ChannelDto.STABLE
        UpdateChannel.BETA -> ChannelDto.BETA
        UpdateChannel.NIGHTLY -> ChannelDto.NIGHTLY
    }

    private inline fun <reified T : Enum<T>> T.wireName(): String =
        ProtocolJson.encodeToString(this).trim('"')
}
