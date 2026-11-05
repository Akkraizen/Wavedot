package space.wavedot.infra.hub

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.domain.sync.PullBatch
import space.wavedot.core.domain.sync.SyncGateway
import space.wavedot.core.domain.sync.SyncRecord
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.core.ProtocolVersion
import space.wavedot.protocol.hub.CreateAccountRequest
import space.wavedot.protocol.hub.CreateAccountResponse
import space.wavedot.protocol.hub.HubRoutes
import space.wavedot.protocol.hub.PullChangesResponse
import space.wavedot.protocol.hub.PushChangesRequest
import space.wavedot.protocol.hub.PushChangesResponse

class HubClient(
    private val http: HttpClient,
    private val settings: HubSettings,
) : SyncGateway {
    override suspend fun isConfigured(): Boolean = settings.connection() != null

    suspend fun createAccount(baseUrl: String, deviceName: String, platform: PlatformDto): HubConnection {
        val url = baseUrl.trimEnd('/')
        val response = http.post(url + HubRoutes.ACCOUNTS) {
            header(ProtocolVersion.HEADER, ProtocolVersion.CURRENT)
            setBody(json(CreateAccountRequest(deviceName, platform)))
        }
        val created = response.decode<CreateAccountResponse>()
        val connection = HubConnection(url, created.deviceToken, created.accountId, created.deviceId)
        settings.save(connection)
        return connection
    }

    suspend fun disconnect() = settings.clear()

    override suspend fun push(records: List<SyncRecord>): Long {
        val connection = requireConnection()
        val response = http.post(connection.baseUrl + HubRoutes.SYNC_CHANGES) {
            authorize(connection)
            setBody(json(PushChangesRequest(records.map(SyncRecordMapper::toDto))))
        }
        return response.decode<PushChangesResponse>().revision
    }

    override suspend fun pull(sinceRevision: Long): PullBatch {
        val connection = requireConnection()
        val response = http.get(connection.baseUrl + HubRoutes.SYNC_CHANGES) {
            authorize(connection)
            parameter(HubRoutes.PARAM_SINCE, sinceRevision)
        }
        val body = response.decode<PullChangesResponse>()
        return PullBatch(
            records = body.changes.mapNotNull { SyncRecordMapper.fromDto(it.change) },
            revision = body.revision,
            hasMore = body.hasMore,
        )
    }

    private suspend fun requireConnection(): HubConnection =
        settings.connection() ?: throw WavedotException(WavedotError.Unauthorized("hub"))

    private fun io.ktor.client.request.HttpRequestBuilder.authorize(connection: HubConnection) {
        bearerAuth(connection.deviceToken)
        header(ProtocolVersion.HEADER, ProtocolVersion.CURRENT)
    }

    private inline fun <reified T> json(value: T) =
        TextContent(ProtocolJson.encodeToString(value), ContentType.Application.Json)

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        val text = bodyAsText()
        if (!status.isSuccess()) {
            val error = if (status == HttpStatusCode.Unauthorized) WavedotError.Unauthorized("hub")
            else WavedotError.Network("Hub responded ${status.value}: $text")
            throw WavedotException(error)
        }
        return ProtocolJson.decodeFromString(text)
    }
}
