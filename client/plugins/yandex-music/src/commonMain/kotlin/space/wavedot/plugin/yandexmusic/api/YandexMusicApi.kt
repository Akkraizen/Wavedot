package space.wavedot.plugin.yandexmusic.api

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import space.wavedot.core.common.TimeProvider
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException

internal class YandexMusicApi(
    private val http: HttpClient,
    private val token: String,
    private val profile: ClientProfile = ClientProfile.ANDROID,
    private val time: TimeProvider,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private var cachedUid: Long? = null

    suspend fun accountUid(): Long {
        cachedUid?.let { return it }
        val status: YmAccountStatus = get("/account/status")
        val uid = status.account.uid ?: throw WavedotException(WavedotError.Unauthorized(SOURCE_NAME))
        cachedUid = uid
        return uid
    }

    suspend fun tracks(ids: List<String>): List<YmTrack> {
        if (ids.isEmpty()) return emptyList()
        return post("/tracks", Parameters.build {
            append("track-ids", ids.joinToString(","))
            append("with-positions", "False")
        })
    }

    suspend fun search(text: String, page: Int): YmSearchSection<YmTrack> =
        search(text, "track", page).tracks ?: YmSearchSection()

    suspend fun searchAlbums(text: String, page: Int): YmSearchSection<YmAlbum> =
        search(text, "album", page).albums ?: YmSearchSection()

    suspend fun searchArtists(text: String, page: Int): YmSearchSection<YmArtist> =
        search(text, "artist", page).artists ?: YmSearchSection()

    suspend fun albumWithTracks(albumId: String): YmAlbum = get("/albums/$albumId/with-tracks")

    suspend fun artistTracks(artistId: String, page: Int, pageSize: Int): YmArtistTracks =
        get("/artists/$artistId/tracks") {
            parameter("page", page)
            parameter("page-size", pageSize)
        }

    suspend fun lyrics(trackId: String, format: String): YmTrackLyrics? {
        val timestamp = time.nowMillis() / 1000
        val response = http.get("$baseUrl/tracks/$trackId/lyrics") {
            authorize()
            parameter("format", format)
            parameter("timeStamp", timestamp)
            parameter("sign", FileInfoSigner.signTrack(profile.signKey, trackId, timestamp))
        }
        if (response.status == HttpStatusCode.NotFound) return null
        return response.unwrap()
    }

    suspend fun download(url: String): String = http.get(url).bodyAsText()

    private suspend fun search(text: String, type: String, page: Int): YmSearch =
        get("/search") {
            parameter("text", text)
            parameter("nocorrect", "False")
            parameter("type", type)
            parameter("page", page)
            parameter("playlist-in-best", "False")
        }

    suspend fun likedTrackIds(): List<YmTrackShort> {
        val uid = accountUid()
        val likes: YmLikes = get("/users/$uid/likes/tracks") { parameter("if-modified-since-revision", 0) }
        return likes.library.tracks
    }

    suspend fun setLiked(trackIds: List<String>, liked: Boolean) {
        val uid = accountUid()
        val action = if (liked) "add-multiple" else "remove"
        post<kotlinx.serialization.json.JsonElement>(
            "/users/$uid/likes/tracks/$action",
            Parameters.build { append("track-ids", trackIds.joinToString(",")) },
        )
    }

    suspend fun fileInfo(trackId: String, quality: String): YmDownloadInfo {
        val timestamp = time.nowMillis() / 1000
        val sign = FileInfoSigner.sign(profile.signKey, timestamp, trackId, quality, CODECS, TRANSPORT)
        val info: YmFileInfo = get("/get-file-info") {
            parameter("ts", timestamp)
            parameter("trackId", trackId)
            parameter("quality", quality)
            parameter("codecs", CODECS.joinToString(","))
            parameter("transports", TRANSPORT)
            parameter("sign", sign)
        }
        return info.downloadInfo
    }

    private suspend inline fun <reified T> get(path: String, crossinline block: HttpRequestBuilder.() -> Unit = {}): T =
        http.get(baseUrl + path) {
            authorize()
            block()
        }.unwrap()

    private suspend inline fun <reified T> post(path: String, form: Parameters): T =
        http.post(baseUrl + path) {
            authorize()
            setBody(FormDataContent(form))
        }.unwrap()

    private fun HttpRequestBuilder.authorize() {
        header(HttpHeaders.Authorization, "OAuth $token")
        header(CLIENT_HEADER, profile.clientHeader)
    }

    private suspend inline fun <reified T> HttpResponse.unwrap(): T {
        val text = bodyAsText()
        if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
            throw WavedotException(WavedotError.Unauthorized(SOURCE_NAME))
        }
        val parsed = runCatching { json.decodeFromString<YmResponse<T>>(text) }.getOrElse {
            throw WavedotException(WavedotError.Network("$SOURCE_NAME responded ${status.value}"), it)
        }
        parsed.error?.let { throw WavedotException(WavedotError.Network("$SOURCE_NAME: ${it.name}: ${it.message}")) }
        return parsed.result ?: throw WavedotException(WavedotError.Network("$SOURCE_NAME returned an empty result"))
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.music.yandex.net"
        const val CLIENT_HEADER: String = "X-Yandex-Music-Client"
        const val TRANSPORT: String = "encraw"
        const val SOURCE_NAME: String = "Yandex Music"
        val CODECS: List<String> = listOf("flac", "flac-mp4", "mp3", "aac", "he-aac", "aac-mp4", "he-aac-mp4")

        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}
