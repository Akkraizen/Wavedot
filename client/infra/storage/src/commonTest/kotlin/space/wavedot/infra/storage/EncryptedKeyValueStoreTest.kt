package space.wavedot.infra.storage

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedKeyValueStoreTest {
    private class MaskingCipher(private val mask: Byte) : SecretCipher {
        override fun encrypt(plain: ByteArray): ByteArray = ByteArray(plain.size) { (plain[it].toInt() xor mask.toInt()).toByte() }

        override fun decrypt(sealed: ByteArray): ByteArray {
            val plain = encrypt(sealed)
            require(plain.all { it >= 0 }) { "wrong key" }
            return plain
        }
    }

    private val raw = InMemoryKeyValueStore()
    private val store = EncryptedKeyValueStore(raw, MaskingCipher(0x5A))

    @Test
    fun storesOnlySealedValues() = runTest {
        store.set("token", "s3cret")
        val stored = raw.get("token")!!
        assertTrue(stored.startsWith("sealed:v1:"))
        assertFalse("s3cret" in stored)
        assertEquals("s3cret", store.get("token"))
        store.delete("token")
        assertNull(raw.get("token"))
    }

    @Test
    fun sealsLegacyPlainValuesOnFirstRead() = runTest {
        raw.set("token", "legacy")
        assertEquals("legacy", store.get("token"))
        assertTrue(raw.get("token")!!.startsWith("sealed:v1:"))
        assertEquals("legacy", store.get("token"))
    }

    @Test
    fun treatsValuesItCannotOpenAsMissing() = runTest {
        store.set("token", "s3cret")
        val other = EncryptedKeyValueStore(raw, MaskingCipher(0xA5.toByte()))
        assertNull(other.get("token"))
        raw.set("broken", "sealed:v1:not base64 !")
        assertNull(store.get("broken"))
    }

    @Test
    fun observesDecryptedValues() = runTest {
        store.observe("token").test {
            assertNull(awaitItem())
            store.set("token", "one")
            assertEquals("one", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
