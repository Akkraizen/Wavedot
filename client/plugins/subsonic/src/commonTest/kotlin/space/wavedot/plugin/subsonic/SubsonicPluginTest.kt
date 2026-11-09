package space.wavedot.plugin.subsonic

import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.InMemoryKeyValueStore
import space.wavedot.core.common.Logger
import space.wavedot.core.common.NoopLogger
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.plugin.api.PluginContext
import space.wavedot.plugin.api.PluginStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubsonicPluginTest {
    private class Store(
        private val delegate: InMemoryKeyValueStore = InMemoryKeyValueStore(),
    ) : PluginStore {
        override suspend fun get(key: String) = delegate.get(key)
        override suspend fun set(key: String, value: String) = delegate.set(key, value)
        override suspend fun delete(key: String) = delegate.delete(key)
        override fun observe(key: String) = delegate.observe(key)
    }

    private suspend fun context(password: String?) = object : PluginContext {
        override val pluginId = SubsonicPlugin.ID
        override val httpClient = FakeSubsonic().client
        override val settings: PluginStore = Store()
        override val secrets: PluginStore = Store()
        override val logger: Logger = NoopLogger
    }.apply {
        settings.set(SubsonicPlugin.SERVER_KEY, "https://music.test")
        settings.set(SubsonicPlugin.USERNAME_KEY, "igor")
        password?.let { secrets.set(SubsonicPlugin.PASSWORD_KEY, it) }
    }

    @Test
    fun contributesNothingUntilConfigured() = runTest {
        assertTrue(SubsonicPlugin().onLoad(context(password = null)).sources.isEmpty())
    }

    @Test
    fun contributesSourceWithValidCredentials() = runTest {
        val sources = SubsonicPlugin().onLoad(context("sesame")).sources
        assertEquals(listOf("Subsonic · music.test"), sources.map { it.descriptor.displayName })
    }

    @Test
    fun failsLoadingWithWrongPassword() = runTest {
        val error = assertFailsWith<WavedotException> { SubsonicPlugin().onLoad(context("wrong")) }
        assertIs<WavedotError.Unauthorized>(error.error)
    }
}
