package space.wavedot.infra.updater

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import space.wavedot.core.domain.update.AppInfo
import space.wavedot.core.domain.update.UpdateChannel
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.protocol.core.ProtocolJson
import space.wavedot.protocol.updates.ArtifactDto
import space.wavedot.protocol.updates.ChannelDto
import space.wavedot.protocol.updates.ReleaseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpUpdateCheckerTest {
    private var lastQuery: String? = null
    private val release = ReleaseDto(
        id = "r1",
        version = "0.2.0",
        channel = ChannelDto.STABLE,
        publishedAt = 1,
        notes = "Fixes",
        mandatory = false,
        artifact = ArtifactDto(PlatformDto.WINDOWS, ArchDto.X64, "wavedot.msi", 10, "abc", "/v1/artifacts/r1/wavedot.msi"),
    )

    private fun checker(status: HttpStatusCode) = HttpUpdateChecker(
        HttpClient(MockEngine { request ->
            lastQuery = request.url.encodedQuery
            if (status == HttpStatusCode.NoContent) respond("", status)
            else respond(ProtocolJson.encodeToString(release), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
        { "https://updates.test/" },
        UpdateTarget(PlatformDto.WINDOWS, ArchDto.X64),
    )

    private val app = AppInfo("0.1.0", UpdateChannel.BETA, "install-1")

    @Test
    fun returnsAvailableUpdateWithAbsoluteUrl() = runTest {
        val update = checker(HttpStatusCode.OK).latest(app)!!
        assertEquals("0.2.0", update.version)
        assertEquals("https://updates.test/v1/artifacts/r1/wavedot.msi", update.downloadUrl)
        assertEquals("platform=windows&arch=x64&channel=beta&current=0.1.0&installId=install-1", lastQuery)
    }

    @Test
    fun noContentMeansUpToDate() = runTest {
        assertNull(checker(HttpStatusCode.NoContent).latest(app))
    }
}
