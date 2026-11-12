package space.wavedot.infra.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.wavedot.core.common.KeyValueStore
import kotlin.io.encoding.Base64

class EncryptedKeyValueStore(
    private val delegate: KeyValueStore,
    private val cipher: SecretCipher,
) : KeyValueStore {
    override suspend fun get(key: String): String? {
        val stored = delegate.get(key) ?: return null
        if (!stored.startsWith(PREFIX)) delegate.set(key, seal(stored))
        return open(stored)
    }

    override suspend fun set(key: String, value: String) = delegate.set(key, seal(value))

    override suspend fun delete(key: String) = delegate.delete(key)

    override fun observe(key: String): Flow<String?> = delegate.observe(key).map { it?.let(::open) }

    private fun seal(value: String): String = PREFIX + Base64.encode(cipher.encrypt(value.encodeToByteArray()))

    private fun open(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return stored
        return runCatching { cipher.decrypt(Base64.decode(stored.removePrefix(PREFIX))).decodeToString() }.getOrNull()
    }

    private companion object {
        const val PREFIX = "sealed:v1:"
    }
}
