package space.wavedot.core.common

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryKeyValueStoreTest {
    @Test
    fun setStoresAndOverwritesValue() = runTest {
        val store = InMemoryKeyValueStore()
        store.set("a", "1")
        store.set("a", "2")
        assertEquals("2", store.get("a"))
    }

    @Test
    fun setKeepsEmptyValue() = runTest {
        val store = InMemoryKeyValueStore(mapOf("a" to "1"))
        store.set("a", "")
        assertEquals("", store.get("a"))
    }

    @Test
    fun deleteRemovesOnlyTheGivenKey() = runTest {
        val store = InMemoryKeyValueStore(mapOf("a" to "1", "b" to "2"))
        store.delete("a")
        assertNull(store.get("a"))
        assertNull(store.observe("a").first())
        assertEquals("2", store.get("b"))
    }

    @Test
    fun deleteOfMissingKeyIsNoop() = runTest {
        val store = InMemoryKeyValueStore(mapOf("b" to "2"))
        store.delete("a")
        assertEquals("2", store.get("b"))
    }
}
