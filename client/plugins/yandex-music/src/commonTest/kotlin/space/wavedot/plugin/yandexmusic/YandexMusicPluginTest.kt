package space.wavedot.plugin.yandexmusic

import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.InMemoryKeyValueStore
import space.wavedot.core.common.Logger
import space.wavedot.core.common.NoopLogger
import space.wavedot.plugin.api.PluginContext
import space.wavedot.plugin.api.PluginStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YandexMusicPluginTest {
    private class Store(
        private val delegate: InMemoryKeyValueStore = InMemoryKeyValueStore(),
    ) : PluginStore {
        override suspend fun get(key: String) = delegate.get(key)
        override suspend fun set(key: String, value: String) = delegate.set(key, value)
        override suspend fun delete(key: String) = delegate.delete(key)
        override fun observe(key: String) = delegate.observe(key)
    }

    private fun context(secrets: PluginStore) = object : PluginContext {
        override val pluginId = YandexMusicPlugin.ID
        override val httpClient = HttpClient(FakeYandexServer().client.engine)
        override val settings: PluginStore = Store()
        override val secrets: PluginStore = secrets
        override val logger: Logger = NoopLogger
    }

    @Test
    fun contributesNothingWithoutToken() = runTest {
        assertTrue(YandexMusicPlugin().onLoad(context(Store())).sources.isEmpty())
    }

    @Test
    fun contributesSourceWhenTokenPresent() = runTest {
        val secrets = Store().apply { set(YandexMusicPlugin.TOKEN_KEY, "secret") }
        val sources = YandexMusicPlugin().onLoad(context(secrets)).sources
        assertEquals(listOf(YandexMusicSource.SOURCE_ID), sources.map { it.descriptor.id })
    }
}
