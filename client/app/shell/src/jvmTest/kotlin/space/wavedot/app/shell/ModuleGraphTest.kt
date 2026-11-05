package space.wavedot.app.shell

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import space.wavedot.core.common.InMemoryKeyValueStore
import space.wavedot.core.common.KeyValueStore
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.data.plugin.PluginManager
import space.wavedot.core.data.plugin.PluginStatus
import space.wavedot.core.domain.update.AppInfo
import space.wavedot.feature.settings.SettingsBackend
import space.wavedot.infra.database.createJvmDatabase
import space.wavedot.infra.updater.UpdateTarget
import space.wavedot.playback.api.AudioEngine
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.source.local.LocalTrack
import space.wavedot.source.local.LocalTrackScanner
import space.wavedot.core.testing.FakeAudioEngine
import space.wavedot.core.testing.TestTracks
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ModuleGraphTest {
    private val testPlatform = module {
        single { HttpClient(MockEngine { respondOk() }) }
        single { createJvmDatabase(null) }
        single<KeyValueStore>(SETTINGS_STORE) { InMemoryKeyValueStore() }
        single<KeyValueStore>(SECRETS_STORE) { InMemoryKeyValueStore() }
        single<AudioEngine> { FakeAudioEngine() }
        single<LocalTrackScanner> {
            object : LocalTrackScanner {
                override suspend fun scan() = listOf(LocalTrack(TestTracks.track("1"), "/a.flac"))
            }
        }
        single {
            PlatformEnvironment(
                app = AppInfo("0.1.0", installId = "test"),
                updateTarget = UpdateTarget(PlatformDto.LINUX, ArchDto.X64),
                platform = PlatformDto.LINUX,
                deviceName = "test",
                supportsMusicFolders = true,
            )
        }
    }

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun graphResolvesAndBootstraps() = runBlocking {
        val koin = koinApplication { modules(wavedotModules(testPlatform)) }.koin
        koin.get<SettingsBackend>()
        koin.bootstrap()
        val manager = koin.get<PluginManager>()
        val entry = withTimeout(5_000) { manager.entries.first { list -> list.all { it.status != PluginStatus.Unloaded } } }.single()
        assertIs<PluginStatus.Loaded>(entry.status)
        assertEquals(listOf("local"), koin.get<MediaSourceRegistry>().sources.value.map { it.descriptor.id.value })
    }
}
