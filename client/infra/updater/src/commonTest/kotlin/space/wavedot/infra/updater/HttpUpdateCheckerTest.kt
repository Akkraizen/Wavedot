package space.wavedot.infra.updater

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class HttpUpdateCheckerTest {
    private var lastQuery: String? = null
    private var requests = 0
    private val expectedManifest = "wavedot-update-v1\n0.2.0\nwindows\nx64\nwavedot.msi\n10\nabc"

    private fun release(
        url: String = "/v1/artifacts/r1/wavedot.msi",
        signature: String? = "good",
        platform: PlatformDto = PlatformDto.WINDOWS,
        fileName: String = "wavedot.msi",
    ) = ReleaseDto(
        id = "r1",
        version = "0.2.0",
        channel = ChannelDto.STABLE,
        publishedAt = 1,
        notes = "Fixes",
        mandatory = false,
        artifact = ArtifactDto(platform, ArchDto.X64, fileName, 10, "ABC", url, signature),
    )

    private fun checker(
        status: HttpStatusCode = HttpStatusCode.OK,
        release: ReleaseDto = release(),
        base: String? = "https://updates.test/",
    ) = HttpUpdateChecker(
        HttpClient(MockEngine { request ->
            requests++
            lastQuery = request.url.encodedQuery
            if (status == HttpStatusCode.NoContent) respond("", status)
            else respond(ProtocolJson.encodeToString(release), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
        { base },
        UpdateTarget(PlatformDto.WINDOWS, ArchDto.X64),
        { manifest, signature -> manifest == expectedManifest && signature == "good" },
    )

    private val app = AppInfo("0.1.0", UpdateChannel.BETA, "install-1")

    private suspend fun assertInvalid(checker: HttpUpdateChecker) {
        val error = assertFailsWith<WavedotException> { checker.latest(app) }
        assertIs<WavedotError.Invalid>(error.error)
    }

    @Test
    fun returnsASignedUpdateWithAnAbsoluteUrl() = runTest {
        val update = checker().latest(app)!!
        assertEquals("0.2.0", update.version)
        assertEquals("wavedot.msi", update.fileName)
        assertEquals("https://updates.test/v1/artifacts/r1/wavedot.msi", update.downloadUrl)
        assertEquals("platform=windows&arch=x64&channel=beta&current=0.1.0&installId=install-1", lastQuery)
    }

    @Test
    fun noContentMeansUpToDate() = runTest {
        assertNull(checker(HttpStatusCode.NoContent).latest(app))
    }

    @Test
    fun refusesUnsignedAndTamperedReleases() = runTest {
        assertInvalid(checker(release = release(signature = null)))
        assertInvalid(checker(release = release(signature = "forged")))
        assertInvalid(checker(release = release(fileName = "other.msi")))
        assertInvalid(checker(release = release(platform = PlatformDto.LINUX)))
        assertInvalid(checker(release = release(fileName = "../wavedot.msi")))
    }

    @Test
    fun refusesDownloadsFromOtherServers() = runTest {
        assertInvalid(checker(release = release(url = "https://evil.test/wavedot.msi")))
        assertInvalid(checker(release = release(url = "//evil.test/wavedot.msi")))
        assertInvalid(checker(release = release(url = "http://updates.test/v1/artifacts/r1/wavedot.msi")))
        assertEquals(
            "https://updates.test/v1/artifacts/r1/wavedot.msi",
            checker(release = release(url = "https://updates.test/v1/artifacts/r1/wavedot.msi")).latest(app)!!.downloadUrl,
        )
    }

    @Test
    fun refusesPlainHttpUpdateServersWithoutContactingThem() = runTest {
        assertInvalid(checker(base = "http://updates.test"))
        assertEquals(0, requests)
        assertNull(checker(base = null).latest(app))
        assertNull(checker(base = " ").latest(app))
    }
}
