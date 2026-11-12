package space.wavedot.server.ktor

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import space.wavedot.protocol.core.ProblemDetails
import space.wavedot.protocol.core.ProblemTypes
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.core.ProtocolVersion
import space.wavedot.server.kernel.AppError
import java.util.UUID

fun interface ReadinessCheck {
    suspend fun isReady(): Boolean
}

private val ProtocolVersionGuard = createApplicationPlugin("ProtocolVersionGuard") {
    onCall { call ->
        val requested = call.request.headers[ProtocolVersion.HEADER] ?: return@onCall
        if (requested.toIntOrNull()?.let { it in 1..ProtocolVersion.CURRENT } != true) {
            call.respondProblem(
                HttpStatusCode.BadRequest,
                ProblemTypes.UNSUPPORTED_PROTOCOL,
                "This server speaks protocol ${ProtocolVersion.CURRENT}",
            )
        }
    }
}

fun Application.installServiceDefaults(
    serviceName: String,
    readiness: ReadinessCheck = ReadinessCheck { true },
    trustForwardedHeaders: Boolean = false,
) {
    val log = LoggerFactory.getLogger(serviceName)
    if (trustForwardedHeaders) install(XForwardedHeaders)
    install(ContentNegotiation) { json(ProtocolJson) }
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) { callIdMdc("call-id") }
    install(StatusPages) {
        exception<AppError> { call, error -> call.respondProblem(error.toStatus(), error.toType(), error.message) }
        exception<PayloadTooLargeException> { call, _ ->
            call.respondProblem(HttpStatusCode.PayloadTooLarge, ProblemTypes.PAYLOAD_TOO_LARGE, "The request body is too large")
        }
        exception<SerializationException> { call, error ->
            log.info("Malformed request on ${call.request.path()}: ${error.message}")
            call.respondProblem(HttpStatusCode.BadRequest, ProblemTypes.VALIDATION, MALFORMED_REQUEST)
        }
        exception<BadRequestException> { call, error ->
            log.info("Malformed request on ${call.request.path()}: ${error.message}")
            call.respondProblem(HttpStatusCode.BadRequest, ProblemTypes.VALIDATION, MALFORMED_REQUEST)
        }
        exception<IllegalArgumentException> { call, error ->
            log.info("Malformed request on ${call.request.path()}: ${error.message}")
            call.respondProblem(HttpStatusCode.BadRequest, ProblemTypes.VALIDATION, MALFORMED_REQUEST)
        }
        exception<Throwable> { call, error ->
            log.error("Unhandled error on ${call.request.path()}", error)
            call.respondProblem(HttpStatusCode.InternalServerError, ProblemTypes.INTERNAL, null)
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respondProblem(status, ProblemTypes.RATE_LIMITED, "Too many requests, retry later")
        }
    }
    install(ProtocolVersionGuard)
    routing {
        get("/health/live") { call.respondText("ok", ContentType.Text.Plain) }
        get("/health/ready") {
            if (readiness.isReady()) call.respondText("ready", ContentType.Text.Plain)
            else call.respondText("not ready", ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
        }
    }
}

suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, type: String, detail: String?) {
    respond(status, ProblemDetails(type = type, title = status.description, status = status.value, detail = detail, instance = request.path()))
}

private const val MALFORMED_REQUEST = "The request is malformed"

private fun AppError.toStatus(): HttpStatusCode = when (this) {
    is AppError.NotFound -> HttpStatusCode.NotFound
    is AppError.Unauthorized -> HttpStatusCode.Unauthorized
    is AppError.Forbidden -> HttpStatusCode.Forbidden
    is AppError.Validation -> HttpStatusCode.BadRequest
    is AppError.Conflict -> HttpStatusCode.Conflict
}

private fun AppError.toType(): String = when (this) {
    is AppError.NotFound -> ProblemTypes.NOT_FOUND
    is AppError.Unauthorized -> ProblemTypes.UNAUTHORIZED
    is AppError.Forbidden -> ProblemTypes.FORBIDDEN
    is AppError.Validation -> ProblemTypes.VALIDATION
    is AppError.Conflict -> ProblemTypes.CONFLICT
}
