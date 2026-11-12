package space.wavedot.plugin.audius

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException

internal class AudiusApi(
    private val http: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun searchTracks(query: String, offset: Int, limit: Int): List<AuTrack> =
        get("/tracks/search") { paged(offset, limit); parameter("query", query) } ?: emptyList()

    suspend fun searchUsers(query: String, offset: Int, limit: Int): List<AuUser> =
        get("/users/search") { paged(offset, limit); parameter("query", query) } ?: emptyList()

    suspend fun searchPlaylists(query: String, offset: Int, limit: Int): List<AuPlaylist> =
        get("/playlists/search") { paged(offset, limit); parameter("query", query) } ?: emptyList()

    suspend fun trending(limit: Int): List<AuTrack> =
        get<List<AuTrack>>("/tracks/trending") { parameter("limit", limit) }.orEmpty().take(limit)

    suspend fun track(id: String): AuTrack? = get("/tracks/$id")

    suspend fun playlistTracks(id: String): List<AuTrack> = get("/playlists/$id/tracks") ?: emptyList()

    suspend fun userTracks(id: String, offset: Int, limit: Int): List<AuTrack> =
        get("/users/$id/tracks") { paged(offset, limit) } ?: emptyList()

    fun streamUrl(id: String): String = "$baseUrl/tracks/$id/stream?app_name=$APP_NAME"

    private fun HttpRequestBuilder.paged(offset: Int, limit: Int) {
        parameter("offset", offset)
        parameter("limit", limit)
    }

    private suspend inline fun <reified T> get(path: String, crossinline block: HttpRequestBuilder.() -> Unit = {}): T? {
        val response = http.get(baseUrl + path) {
            parameter("app_name", APP_NAME)
            block()
        }
        if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.BadRequest) return null
        return response.decode<AuResponse<T>>().data
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        if (!status.isSuccess()) throw WavedotException(WavedotError.Network("$SOURCE_NAME responded ${status.value}"))
        val text = bodyAsText()
        return runCatching { json.decodeFromString<T>(text) }.getOrElse {
            throw WavedotException(WavedotError.Network("$SOURCE_NAME returned an unexpected response"), it)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.audius.co/v1"
        const val APP_NAME: String = "Wavedot"
        const val SOURCE_NAME: String = "Audius"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
