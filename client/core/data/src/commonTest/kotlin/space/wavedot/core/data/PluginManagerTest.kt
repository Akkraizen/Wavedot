package space.wavedot.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.NoopLogger
import space.wavedot.core.data.plugin.DefaultPluginContextFactory
import space.wavedot.core.common.InMemoryKeyValueStore
import space.wavedot.core.data.plugin.PluginManager
import space.wavedot.core.data.plugin.PluginStatus
import space.wavedot.core.contract.LyricsProvider
import space.wavedot.core.contract.LyricsRequest
import space.wavedot.core.data.source.DefaultLyricsProviderRegistry
import space.wavedot.core.data.source.DefaultMediaSourceRegistry
import space.wavedot.core.model.lyrics.Lyrics
import space.wavedot.core.model.SourceId
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.plugin.api.PluginContext
import space.wavedot.plugin.api.PluginContribution
import space.wavedot.plugin.api.PluginManifest
import space.wavedot.plugin.api.WavedotPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PluginManagerTest {
    private class SamplePlugin(id: String, private val fail: Boolean = false, apiVersion: Int = 1) : WavedotPlugin {
        override val manifest = PluginManifest(id, id, "1.0", apiVersion = apiVersion)
        var lastToken: String? = null

        override suspend fun onLoad(context: PluginContext): PluginContribution {
            check(!fail) { "broken" }
            lastToken = context.secrets.get("token")
            return PluginContribution(
                sources = listOf(FakeMediaSource(SourceId(manifest.id))),
                lyricsProviders = listOf(object : LyricsProvider {
                    override val id = "${manifest.id}-lyrics"
                    override val displayName = "Lyrics"
                    override suspend fun find(request: LyricsRequest): Lyrics? = null
                }),
            )
        }
    }

    private val registry = DefaultMediaSourceRegistry()
    private val lyricsRegistry = DefaultLyricsProviderRegistry()
    private val secrets = InMemoryKeyValueStore(mapOf("plugin.good.token" to "secret"))
    private val contextFactory = DefaultPluginContextFactory(
        HttpClient(MockEngine { respondOk() }),
        InMemoryKeyValueStore(),
        secrets,
        NoopLogger,
    )

    @Test
    fun loadsPluginsAndRegistersTheirSources() = runTest {
        val good = SamplePlugin("good")
        val manager = PluginManager(listOf(good, SamplePlugin("bad", fail = true)), registry, lyricsRegistry, contextFactory)
        manager.loadAll()

        assertEquals(listOf(SourceId("good")), registry.sources.value.map { it.descriptor.id })
        assertEquals(listOf("good-lyrics"), lyricsRegistry.providers.value.map { it.id })
        assertEquals("secret", good.lastToken)
        val statuses = manager.entries.value.associate { it.manifest.id to it.status }
        assertIs<PluginStatus.Loaded>(statuses["good"])
        assertIs<PluginStatus.Failed>(statuses["bad"])
    }

    @Test
    fun unloadRemovesSources() = runTest {
        val manager = PluginManager(listOf(SamplePlugin("good")), registry, lyricsRegistry, contextFactory)
        manager.load("good")
        manager.unload("good")
        assertTrue(registry.sources.value.isEmpty())
        assertTrue(lyricsRegistry.providers.value.isEmpty())
        assertEquals(PluginStatus.Unloaded, manager.entries.value.single().status)
    }

    @Test
    fun rejectsIncompatibleApiVersion() = runTest {
        val manager = PluginManager(listOf(SamplePlugin("future", apiVersion = 99)), registry, lyricsRegistry, contextFactory)
        assertIs<PluginStatus.Failed>(manager.load("future"))
        assertTrue(registry.sources.value.isEmpty())
    }
}
