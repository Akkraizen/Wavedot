package space.wavedot.infra.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

fun defaultHttpClient(settings: HttpClientSettings = HttpClientSettings()): HttpClient =
    createHttpClient(OkHttp.create(), settings)
