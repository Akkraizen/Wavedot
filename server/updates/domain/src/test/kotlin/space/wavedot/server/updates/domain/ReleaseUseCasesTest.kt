package space.wavedot.server.updates.domain

import kotlinx.coroutines.test.runTest
import space.wavedot.server.kernel.AppError
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseUseCasesTest {
    private val store = InMemoryReleases()
    private var ids = 0
    private var now = Instant.EPOCH
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val catalog = ReleaseCatalog(store, clock)
    private val publish = PublishRelease(store, store, { "r${++ids}" }, clock, catalog)
    private val resolve = ResolveLatest(catalog)

    private suspend fun release(version: String, channel: Channel = Channel.STABLE, rollout: Int = 100, arch: Arch = Arch.X64) =
        publish(
            PublishCommand(
                version, channel, "notes", false, rollout,
                listOf(ArtifactUpload(Platform.WINDOWS, arch, "wavedot-$version.msi", "bin-$version".byteInputStream())),
            ),
        )

    private fun query(current: String = "0.1.0", channel: Channel = Channel.STABLE, arch: Arch = Arch.X64, install: String = "i") =
        UpdateQuery(Platform.WINDOWS, arch, channel, SemVer.parse(current), install)

    @Test
    fun semVerOrdering() {
        val ordered = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.10.0")
        assertEquals(ordered, ordered.shuffled().map(SemVer::parse).sorted().map(SemVer::toString))
        assertFailsWith<AppError.Validation> { SemVer.parse("1.0") }
    }

    @Test
    fun picksNewestReleaseAllowedByChannel() = runTest {
        release("0.2.0")
        release("0.3.0-beta.1", Channel.BETA)
        assertEquals("0.2.0", resolve(query())!!.release.version.toString())
        assertEquals("0.3.0-beta.1", resolve(query(channel = Channel.BETA))!!.release.version.toString())
        assertNull(resolve(query(current = "0.2.0")))
    }

    @Test
    fun skipsYankedReleasesAndUnsupportedArchitectures() = runTest {
        val bad = release("0.2.0")
        release("0.1.5")
        YankRelease(store, catalog)(bad.id)
        assertEquals("0.1.5", resolve(query())!!.release.version.toString())
        assertNull(resolve(query(arch = Arch.ARM64)))
    }

    @Test
    fun universalArtifactsMatchAnyArchitecture() = runTest {
        release("0.2.0", arch = Arch.UNIVERSAL)
        assertEquals(Arch.UNIVERSAL, resolve(query(arch = Arch.ARM64))!!.artifact.arch)
    }

    @Test
    fun staggeredRolloutIsDeterministicPerInstall() = runTest {
        release("0.2.0", rollout = 30)
        val offered = (1..1000).count { resolve(query(install = "install-$it")) != null }
        assertTrue(offered in 230..370, "offered to $offered of 1000")
        val first = resolve(query(install = "install-7")) != null
        assertEquals(first, resolve(query(install = "install-7")) != null)
    }

    @Test
    fun rejectsDuplicatesAndBadInput() = runTest {
        release("0.2.0")
        assertFailsWith<AppError.Conflict> { release("0.2.0") }
        assertFailsWith<AppError.Validation> {
            publish(PublishCommand("0.4.0", Channel.STABLE, "", false, 100, listOf(ArtifactUpload(Platform.LINUX, Arch.X64, "../evil", "x".byteInputStream()))))
        }
        assertFailsWith<AppError.Validation> { publish(PublishCommand("0.5.0", Channel.STABLE, "", false, 100, emptyList())) }
    }

    @Test
    fun rejectsHiddenAndRelativeFileNames() = runTest {
        listOf(".", "..", ".hidden", "a/b", "").forEach { name ->
            assertFailsWith<AppError.Validation>(name) {
                publish(PublishCommand("0.9.0", Channel.STABLE, "", false, 100, listOf(ArtifactUpload(Platform.LINUX, Arch.X64, name, "x".byteInputStream()))))
            }
        }
        assertTrue(store.blobs.isEmpty())
    }

    @Test
    fun cachesTheCatalogUntilItChangesOrExpires() = runTest {
        release("0.2.0")
        repeat(5) { resolve(query()) }
        assertEquals(1, store.listings)

        release("0.3.0")
        assertEquals("0.3.0", resolve(query())!!.release.version.toString())
        assertEquals(2, store.listings)

        now = now.plusSeconds(31)
        resolve(query())
        assertEquals(3, store.listings)
    }

    @Test
    fun servesArtifactsOfActiveReleasesOnly() = runTest {
        val published = release("0.2.0")
        val getArtifact = GetArtifact(store, store)
        val download = getArtifact(published.id, "wavedot-0.2.0.msi")
        assertEquals(ArtifactLocation.Remote("memory://${published.id}/wavedot-0.2.0.msi"), download.location)
        assertFailsWith<AppError.NotFound> { getArtifact(published.id, "other.msi") }

        YankRelease(store, catalog)(published.id)
        assertFailsWith<AppError.NotFound> { getArtifact(published.id, "wavedot-0.2.0.msi") }
    }

    @Test
    fun requiresValidSignaturesWhenAVerifierIsConfigured() = runTest {
        val signed = PublishRelease(store, store, { "s${++ids}" }, clock, catalog) { version, artifact, signature ->
            signature == "sig:$version:${artifact.sha256}:${artifact.size}"
        }
        fun command(version: String, signature: String?) = PublishCommand(
            version, Channel.STABLE, "", false, 100,
            listOf(ArtifactUpload(Platform.WINDOWS, Arch.X64, "app.msi", "bin".byteInputStream(), signature)),
        )
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest("bin".toByteArray()).joinToString("") { "%02x".format(it) }

        assertFailsWith<AppError.Validation> { signed(command("1.0.0", null)) }
        assertFailsWith<AppError.Validation> { signed(command("1.0.0", "sig:forged")) }
        assertTrue(store.blobs.isEmpty())
        assertNull(store.findByVersion(SemVer.parse("1.0.0")))

        val release = signed(command("1.0.0", "sig:1.0.0:$sha:3"))
        assertEquals("sig:1.0.0:$sha:3", release.artifacts.single().signature)
    }

    @Test
    fun rejectsOverlongInstallIds() = runTest {
        release("0.2.0")
        assertFailsWith<AppError.Validation> { resolve(query(install = "x".repeat(129))) }
    }
}
