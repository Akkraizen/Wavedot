package space.wavedot.server.hub.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import kotlinx.serialization.json.JsonElement
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.hub.ChangeDto
import space.wavedot.protocol.hub.CreateAccountRequest
import space.wavedot.protocol.hub.CreateAccountResponse
import space.wavedot.protocol.hub.HubRoutes
import space.wavedot.protocol.hub.PullChangesResponse
import space.wavedot.protocol.hub.PushChangesRequest
import space.wavedot.protocol.hub.PushChangesResponse
import space.wavedot.protocol.hub.RejectedChangeDto
import space.wavedot.protocol.hub.RevisionEvent
import space.wavedot.protocol.hub.RevisionedChangeDto
import space.wavedot.server.hub.domain.account.AuthenticateDevice
import space.wavedot.server.hub.domain.account.CreateAccount
import space.wavedot.server.hub.domain.account.DeviceSession
import space.wavedot.server.hub.domain.sync.Change
import space.wavedot.server.hub.domain.sync.PullChanges
import space.wavedot.server.hub.domain.sync.PushChanges
import space.wavedot.server.kernel.AppError
import space.wavedot.server.ktor.bearerToken

class HubUseCases(
    val createAccount: CreateAccount,
    val authenticate: AuthenticateDevice,
    val push: PushChanges,
    val pull: PullChanges,
    val events: RevisionEvents,
)

const val DEVICE_AUTH = "device"

fun Application.hubApi(useCases: HubUseCases) {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ProtocolJson)
    }
    install(Authentication) {
        bearerToken(DEVICE_AUTH) { token -> useCases.authenticate(token) }
    }
    routing {
        accountRoutes(useCases)
        authenticate(DEVICE_AUTH) {
            syncRoutes(useCases)
        }
    }
}

private fun Route.accountRoutes(useCases: HubUseCases) {
    post(HubRoutes.ACCOUNTS) {
        val request = call.receive<CreateAccountRequest>()
        val platform = ProtocolJson.encodeToString(request.platform).trim('"')
        val registered = useCases.createAccount(request.deviceName, platform)
        call.respond(
            HttpStatusCode.Created,
            CreateAccountResponse(registered.accountId.value, registered.deviceId.value, registered.token),
        )
    }
}

private fun Route.syncRoutes(useCases: HubUseCases) {
    get(HubRoutes.SYNC_CHANGES) {
        val session = call.session()
        val since = call.request.queryParameters[HubRoutes.PARAM_SINCE]?.toLongOrNull() ?: 0L
        val limit = call.request.queryParameters[HubRoutes.PARAM_LIMIT]?.toIntOrNull() ?: 500
        val result = useCases.pull(session, since, limit)
        call.respond(
            PullChangesResponse(
                changes = result.changes.map { RevisionedChangeDto(it.revision, it.deviceId.value, it.change.toDto()) },
                revision = result.revision,
                hasMore = result.hasMore,
            ),
        )
    }
    post(HubRoutes.SYNC_CHANGES) {
        val session = call.session()
        val request = call.receive<PushChangesRequest>()
        val result = useCases.push(session, request.changes.map { it.toDomain() })
        call.respond(
            PushChangesResponse(
                revision = result.revision,
                rejected = result.rejected.map { RejectedChangeDto(it.change.collection, it.change.key, it.reason) },
            ),
        )
    }
    webSocket(HubRoutes.SYNC_EVENTS) {
        val session = call.principal<DeviceSession>() ?: return@webSocket
        useCases.events.subscribe(session.accountId).collect { revision -> sendSerialized(RevisionEvent(revision)) }
    }
}

private fun io.ktor.server.application.ApplicationCall.session(): DeviceSession =
    principal<DeviceSession>() ?: throw AppError.Unauthorized()

private fun ChangeDto.toDomain() = Change(
    collection = collection,
    key = key,
    deleted = deleted,
    payload = payload?.let { ProtocolJson.encodeToString(JsonElement.serializer(), it) },
    updatedAt = updatedAt,
)

private fun Change.toDto() = ChangeDto(
    collection = collection,
    key = key,
    deleted = deleted,
    payload = payload?.let { ProtocolJson.parseToJsonElement(it) },
    updatedAt = updatedAt,
)
