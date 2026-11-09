package space.wavedot.plugin.radiobrowser

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException

internal class RadioBrowserApi(
    private val http: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun search(name: String, offset: Int, limit: Int): List<RbStation> = get("/json/stations/search") {
        parameter("name", name)
        parameter("offset", offset)
        parameter("limit", limit)
        parameter("hidebroken", true)
        parameter("order", "clickcount")
        parameter("reverse", true)
    }

    suspend fun stations(uuids: List<String>): List<RbStation> {
        if (uuids.isEmpty()) return emptyList()
        return get("/json/stations/byuuid") { parameter("uuids", uuids.joinToString(",")) }
    }

    suspend fun streamUrl(uuid: String): RbStreamUrl = get("/json/url/$uuid")

    private suspend inline fun <reified T> get(path: String, crossinline block: HttpRequestBuilder.() -> Unit = {}): T =
        http.get(baseUrl + path) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            block()
        }.decode()

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        if (!status.isSuccess()) throw WavedotException(WavedotError.Network("$SOURCE_NAME responded ${status.value}"))
        val text = bodyAsText()
        return runCatching { json.decodeFromString<T>(text) }.getOrElse {
            throw WavedotException(WavedotError.Network("$SOURCE_NAME returned an unexpected response"), it)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://all.api.radio-browser.info"
        const val USER_AGENT: String = "Wavedot/0.1"
        const val SOURCE_NAME: String = "Radio Browser"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
