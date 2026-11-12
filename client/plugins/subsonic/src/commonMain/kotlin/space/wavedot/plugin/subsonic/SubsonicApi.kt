package space.wavedot.plugin.subsonic

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException

internal class SubsonicApi(
    private val http: HttpClient,
    private val config: SubsonicConfig,
    private val salt: String = randomSalt(),
) {
    private val token = md5Hex(config.password + salt)

    suspend fun ping() {
        call<JsonObject>("ping")
    }

    suspend fun search(
        query: String,
        songs: Pair<Int, Int> = NONE,
        albums: Pair<Int, Int> = NONE,
        artists: Pair<Int, Int> = NONE,
    ): SsSearchResult = call<SsSearchResponse>(
        "search3",
        "query" to query,
        "songCount" to songs.first.toString(),
        "songOffset" to songs.second.toString(),
        "albumCount" to albums.first.toString(),
        "albumOffset" to albums.second.toString(),
        "artistCount" to artists.first.toString(),
        "artistOffset" to artists.second.toString(),
    ).searchResult3

    suspend fun song(id: String): SsSong? = callOrNull<SsSongResponse>("getSong", "id" to id)?.song

    suspend fun album(id: String): SsAlbum? = callOrNull<SsAlbumResponse>("getAlbum", "id" to id)?.album

    suspend fun artist(id: String): SsArtist? = callOrNull<SsArtistResponse>("getArtist", "id" to id)?.artist

    suspend fun starredSongs(): List<SsSong> = call<SsStarredResponse>("getStarred2").starred2.song

    suspend fun star(id: String) {
        call<JsonObject>("star", "id" to id)
    }

    suspend fun unstar(id: String) {
        call<JsonObject>("unstar", "id" to id)
    }

    fun streamUrl(id: String, maxBitRate: Int?): String =
        url("stream", listOfNotNull("id" to id, maxBitRate?.let { "maxBitRate" to it.toString() }))

    fun coverArtUrl(id: String): String = url("getCoverArt", listOf("id" to id, "size" to COVER_SIZE))

    private suspend inline fun <reified T> callOrNull(method: String, vararg params: Pair<String, String>): T? =
        try {
            call<T>(method, *params)
        } catch (e: WavedotException) {
            if (e.error is WavedotError.NotFound) null else throw e
        }

    private suspend inline fun <reified T> call(method: String, vararg params: Pair<String, String>): T {
        val response = http.get(url(method, params.toList() + ("f" to "json")))
        if (!response.status.isSuccess()) throw WavedotException(WavedotError.Network("$SOURCE_NAME responded ${response.status.value}"))
        val body = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject[ENVELOPE]!!.jsonObject }.getOrElse {
            throw WavedotException(WavedotError.Network("$SOURCE_NAME returned an unexpected response"), it)
        }
        if (body["status"]?.jsonPrimitive?.content != "ok") throw failure(body["error"]?.let { json.decodeFromJsonElement<SsError>(it) })
        return runCatching { json.decodeFromJsonElement<T>(body) }.getOrElse {
            throw WavedotException(WavedotError.Network("$SOURCE_NAME returned an unexpected $method response"), it)
        }
    }

    private fun failure(error: SsError?): WavedotException = when (error?.code) {
        WRONG_CREDENTIALS, TOKEN_UNSUPPORTED, NOT_AUTHORIZED -> WavedotException(WavedotError.Unauthorized(SOURCE_NAME))
        NOT_FOUND -> WavedotException(WavedotError.NotFound(error.message))
        else -> WavedotException(WavedotError.Network("$SOURCE_NAME: ${error?.message ?: "request failed"}"))
    }

    private fun url(method: String, params: List<Pair<String, String>>): String =
        URLBuilder().takeFrom("${config.restUrl}/$method.view").apply {
            parameters.append("u", config.username)
            parameters.append("t", token)
            parameters.append("s", salt)
            parameters.append("v", API_VERSION)
            parameters.append("c", CLIENT)
            params.forEach { (key, value) -> parameters.append(key, value) }
        }.buildString()

    companion object {
        const val SOURCE_NAME: String = "Subsonic"
        const val API_VERSION: String = "1.16.1"
        const val CLIENT: String = "Wavedot"
        private const val ENVELOPE = "subsonic-response"
        private const val COVER_SIZE = "480"
        private const val WRONG_CREDENTIALS = 40
        private const val TOKEN_UNSUPPORTED = 41
        private const val NOT_AUTHORIZED = 50
        private const val NOT_FOUND = 70
        private val NONE = 0 to 0
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        private const val SALT_BYTES = 16

        private fun randomSalt(): String =
            secureRandomBytes(SALT_BYTES).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
