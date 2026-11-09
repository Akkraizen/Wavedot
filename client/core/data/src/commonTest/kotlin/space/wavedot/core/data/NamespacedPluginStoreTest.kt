package space.wavedot.core.data

import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.InMemoryKeyValueStore
import space.wavedot.core.data.plugin.NamespacedPluginStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NamespacedPluginStoreTest {
    @Test
    fun setWritesUnderPluginNamespace() = runTest {
        val backing = InMemoryKeyValueStore()
        NamespacedPluginStore(backing, "demo").set("token", "secret")
        assertEquals("secret", backing.get("plugin.demo.token"))
    }

    @Test
    fun deleteRemovesOnlyThePluginKey() = runTest {
        val backing = InMemoryKeyValueStore(
            mapOf(
                "plugin.demo.token" to "a",
                "plugin.other.token" to "b",
            ),
        )
        val store = NamespacedPluginStore(backing, "demo")
        store.delete("token")
        assertNull(store.get("token"))
        assertEquals("b", backing.get("plugin.other.token"))
    }
}
