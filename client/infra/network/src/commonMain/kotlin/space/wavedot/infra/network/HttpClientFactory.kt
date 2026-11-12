package space.wavedot.infra.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

data class HttpClientSettings(
    val userAgent: String = "Wavedot",
    val connectTimeoutMillis: Long = 15_000,
    val requestTimeoutMillis: Long? = null,
    val socketTimeoutMillis: Long = 30_000,
)

fun createHttpClient(
    engine: HttpClientEngine,
    settings: HttpClientSettings = HttpClientSettings(),
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(HttpTimeout) {
        connectTimeoutMillis = settings.connectTimeoutMillis
        requestTimeoutMillis = settings.requestTimeoutMillis
        socketTimeoutMillis = settings.socketTimeoutMillis
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, settings.userAgent)
    }
    configure()
}
