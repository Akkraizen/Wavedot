package space.wavedot.server.hub.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import kotlinx.serialization.json.JsonElement
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.core.wireName
import space.wavedot.protocol.hub.ChangeDto
import space.wavedot.protocol.hub.CreateAccountRequest
import space.wavedot.protocol.hub.CreateAccountResponse
import space.wavedot.protocol.hub.DeviceDto
import space.wavedot.protocol.hub.DevicesResponse
import space.wavedot.protocol.hub.HubRoutes
import space.wavedot.protocol.hub.PullChangesResponse
import space.wavedot.protocol.hub.PushChangesRequest
import space.wavedot.protocol.hub.PushChangesResponse
import space.wavedot.protocol.hub.RejectedChangeDto
import space.wavedot.protocol.hub.RevisionEvent
import space.wavedot.protocol.hub.RevisionedChangeDto
import space.wavedot.server.hub.domain.account.AuthenticateDevice
import space.wavedot.server.hub.domain.account.CreateAccount
import space.wavedot.server.hub.domain.account.DeleteAccount
import space.wavedot.server.hub.domain.account.Device
import space.wavedot.server.hub.domain.account.DeviceId
import space.wavedot.server.hub.domain.account.DeviceSession
import space.wavedot.server.hub.domain.account.ListDevices
import space.wavedot.server.hub.domain.account.RevokeDevice
import space.wavedot.server.hub.domain.sync.Change
import space.wavedot.server.hub.domain.sync.PullChanges
import space.wavedot.server.hub.domain.sync.PushChanges
import space.wavedot.server.hub.domain.sync.RevisionFeed
import space.wavedot.server.kernel.AppError
import space.wavedot.server.ktor.bearerToken
import space.wavedot.server.ktor.perRemoteHost
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HubUseCases(
    val createAccount: CreateAccount,
    val authenticate: AuthenticateDevice,
    val push: PushChanges,
    val pull: PullChanges,
    val events: RevisionFeed,
    val listDevices: ListDevices,
    val revokeDevice: RevokeDevice,
    val deleteAccount: DeleteAccount,
)

data class HubApiLimits(
    val registrationsPerHour: Int = 10,
    val requestsPerMinute: Int = 600,
    val maxRegistrationBodyBytes: Long = 8 * 1024,
    val maxSyncBodyBytes: Long = 12L * 1024 * 1024,
)

const val DEVICE_AUTH = "device"

private val REGISTRATION_LIMIT = RateLimitName("registration")
private val API_LIMIT = RateLimitName("api")

fun Application.hubApi(useCases: HubUseCases, limits: HubApiLimits = HubApiLimits()) {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ProtocolJson)
        pingPeriod = 30.seconds
        timeout = 15.seconds
        maxFrameSize = MAX_INBOUND_FRAME_BYTES
    }
    install(RequestBodyLimit) {
        bodyLimit { call ->
            if (call.request.path() == HubRoutes.ACCOUNTS) limits.maxRegistrationBodyBytes else limits.maxSyncBodyBytes
        }
    }
    install(RateLimit) {
        perRemoteHost(REGISTRATION_LIMIT, limits.registrationsPerHour, 1.hours)
        perRemoteHost(API_LIMIT, limits.requestsPerMinute, 1.minutes)
    }
    install(Authentication) {
        bearerToken(DEVICE_AUTH) { token -> useCases.authenticate(token) }
    }
    routing {
        rateLimit(REGISTRATION_LIMIT) { registrationRoutes(useCases) }
        rateLimit(API_LIMIT) {
            authenticate(DEVICE_AUTH) {
                accountRoutes(useCases)
                syncRoutes(useCases)
            }
        }
    }
}

private fun Route.registrationRoutes(useCases: HubUseCases) {
    post(HubRoutes.ACCOUNTS) {
        val request = call.receive<CreateAccountRequest>()
        val registered = useCases.createAccount(request.deviceName, request.platform.wireName(), request.registrationToken)
        call.respond(
            HttpStatusCode.Created,
            CreateAccountResponse(registered.accountId.value, registered.deviceId.value, registered.token),
        )
    }
}

private fun Route.accountRoutes(useCases: HubUseCases) {
    get(HubRoutes.DEVICES) {
        val session = call.session()
        call.respond(DevicesResponse(useCases.listDevices(session).map { it.toDto(session) }))
    }
    delete("${HubRoutes.DEVICES}/{id}") {
        useCases.revokeDevice(call.session(), DeviceId(call.parameters["id"]!!))
        call.respond(HttpStatusCode.NoContent)
    }
    delete(HubRoutes.ACCOUNT) {
        useCases.deleteAccount(call.session())
        call.respond(HttpStatusCode.NoContent)
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

private const val MAX_INBOUND_FRAME_BYTES = 1024L

private fun ApplicationCall.session(): DeviceSession =
    principal<DeviceSession>() ?: throw AppError.Unauthorized()

private fun Device.toDto(session: DeviceSession) = DeviceDto(
    id = id.value,
    name = name,
    platform = platform,
    createdAt = createdAt.toEpochMilli(),
    lastSeenAt = lastSeenAt.toEpochMilli(),
    current = id == session.deviceId,
)

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
