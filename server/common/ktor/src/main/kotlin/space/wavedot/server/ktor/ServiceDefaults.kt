package space.wavedot.server.ktor

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
import space.wavedot.server.kernel.AppError
import java.util.UUID

fun interface ReadinessCheck {
    suspend fun isReady(): Boolean
}

fun Application.installServiceDefaults(serviceName: String, readiness: ReadinessCheck = ReadinessCheck { true }) {
    val log = LoggerFactory.getLogger(serviceName)
    install(ContentNegotiation) { json(ProtocolJson) }
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) { callIdMdc("call-id") }
    install(StatusPages) {
        exception<AppError> { call, error -> call.respondProblem(error.toStatus(), error.toType(), error.message) }
        exception<SerializationException> { call, error ->
            call.respondProblem(HttpStatusCode.BadRequest, ProblemTypes.VALIDATION, error.message)
        }
        exception<IllegalArgumentException> { call, error ->
            call.respondProblem(HttpStatusCode.BadRequest, ProblemTypes.VALIDATION, error.message)
        }
        exception<Throwable> { call, error ->
            log.error("Unhandled error on ${call.request.path()}", error)
            call.respondProblem(HttpStatusCode.InternalServerError, ProblemTypes.INTERNAL, null)
        }
    }
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
