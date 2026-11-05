package space.wavedot.infra.hub

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import space.wavedot.core.common.KeyValueStore

data class HubConnection(
    val baseUrl: String,
    val deviceToken: String,
    val accountId: String,
    val deviceId: String,
)

class HubSettings(private val store: KeyValueStore) {
    suspend fun connection(): HubConnection? {
        val url = store.get(KEY_URL) ?: return null
        val token = store.get(KEY_TOKEN) ?: return null
        return HubConnection(url, token, store.get(KEY_ACCOUNT).orEmpty(), store.get(KEY_DEVICE).orEmpty())
    }

    fun observeConnected(): Flow<Boolean> =
        combine(store.observe(KEY_URL), store.observe(KEY_TOKEN)) { url, token -> url != null && token != null }

    suspend fun save(connection: HubConnection) {
        store.set(KEY_URL, connection.baseUrl.trimEnd('/'))
        store.set(KEY_TOKEN, connection.deviceToken)
        store.set(KEY_ACCOUNT, connection.accountId)
        store.set(KEY_DEVICE, connection.deviceId)
    }

    suspend fun clear() {
        listOf(KEY_URL, KEY_TOKEN, KEY_ACCOUNT, KEY_DEVICE).forEach { store.set(it, null) }
    }

    private companion object {
        const val KEY_URL = "hub.url"
        const val KEY_TOKEN = "hub.token"
        const val KEY_ACCOUNT = "hub.account"
        const val KEY_DEVICE = "hub.device"
    }
}
