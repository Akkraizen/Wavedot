package space.wavedot.plugin.yandexmusic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal class FakeYandexServer {
    val requests = mutableListOf<HttpRequestData>()
    val likedChanges = mutableListOf<String>()
    var token = "secret"

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun track(id: String, title: String) = """
        {"id":"$id","title":"$title","version":"Remastered","durationMs":215000,
         "coverUri":"avatars.yandex.net/get-music-content/1/abc/%%",
         "artists":[{"id":7,"name":"Kino"}],
         "albums":[{"id":99,"title":"Gruppa krovi","year":1988}],"available":true}
    """.trimIndent()

    val client = HttpClient(MockEngine { request -> handle(request) })

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        requests += request
        if (request.headers[HttpHeaders.Authorization] != "OAuth $token") {
            return respond("""{"error":{"name":"session-expired","message":"no"}}""", HttpStatusCode.Unauthorized, json)
        }
        val path = request.url.encodedPath
        return when {
            path == "/account/status" -> ok("""{"account":{"uid":1001}}""")
            path == "/tracks" && request.method == HttpMethod.Post -> {
                val body = request.body.toByteArray().decodeToString()
                val ids = body.substringAfter("track-ids=").substringBefore('&').replace("%2C", ",").split(',')
                ok(ids.joinToString(",", "[", "]") { track(it, "Song $it") })
            }
            path == "/search" && request.url.parameters["type"] == "album" ->
                ok("""{"albums":{"total":1,"perPage":10,"results":[{"id":99,"title":"Gruppa krovi","year":1988,"trackCount":11,
                   "coverUri":"avatars.yandex.net/get-music-content/2/def/%%","artists":[{"id":7,"name":"Kino"}]}]}}""")
            path == "/search" && request.url.parameters["type"] == "artist" ->
                ok("""{"artists":{"total":1,"perPage":10,"results":[{"id":7,"name":"Kino","cover":{"type":"from-artist-photos","uri":"avatars.yandex.net/get-music-content/3/ghi/%%"}}]}}""")
            path == "/search" -> {
                val page = request.url.parameters["page"]!!.toInt()
                val results = (0 until 2).joinToString(",") { track("${page * 2 + it + 1}", "Pachka sigaret") }
                ok("""{"tracks":{"total":5,"perPage":2,"results":[$results]}}""")
            }
            path == "/albums/99/with-tracks" ->
                ok("""{"id":99,"title":"Gruppa krovi","volumes":[[${track("101", "Gruppa krovi")},${track("102", "Zakrytaya dver")}],[${track("103", "Bonus")}]]}""")
            path == "/artists/7/tracks" -> {
                val page = request.url.parameters["page"]!!.toInt()
                val tracks = if (page == 0) "${track("201", "Kukushka")},${track("202", "Peremen")}" else track("203", "Krasno-zheltye dni")
                ok("""{"tracks":[$tracks],"pager":{"page":$page,"perPage":2,"total":3}}""")
            }
            path == "/users/1001/likes/tracks" ->
                ok("""{"library":{"uid":1001,"revision":3,"tracks":[{"id":"10","albumId":"1"},{"id":"11:2","albumId":"2"},{"id":"12"}]}}""")
            path.startsWith("/users/1001/likes/tracks/") -> {
                likedChanges += path.substringAfterLast('/') + ":" + request.body.toByteArray().decodeToString()
                ok("""{"revision":4}""")
            }
            path == "/get-file-info" -> ok(
                """{"downloadInfo":{"trackId":"${request.url.parameters["trackId"]}","quality":"lossless","codec":"flac-mp4",
                   "urls":["https://strm.test/file"],"key":"000102030405060708090a0b0c0d0e0f","bitrate":0,"transport":"encraw"}}""",
            )
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private fun MockRequestHandleScope.ok(result: String) =
        respond("""{"invocationInfo":{},"result":$result}""", HttpStatusCode.OK, json)
}
