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

class HubSettings(
    private val settings: KeyValueStore,
    private val secrets: KeyValueStore,
) {
    suspend fun connection(): HubConnection? {
        val url = settings.get(KEY_URL) ?: return null
        val token = token() ?: return null
        return HubConnection(
            baseUrl = url,
            deviceToken = token,
            accountId = settings.get(KEY_ACCOUNT).orEmpty(),
            deviceId = settings.get(KEY_DEVICE).orEmpty(),
        )
    }

    fun observeConnected(): Flow<Boolean> =
        combine(settings.observe(KEY_URL), secrets.observe(KEY_TOKEN), settings.observe(KEY_TOKEN)) { url, token, legacy ->
            url != null && (token != null || legacy != null)
        }

    suspend fun save(connection: HubConnection) {
        secrets.set(KEY_TOKEN, connection.deviceToken)
        settings.delete(KEY_TOKEN)
        settings.set(KEY_ACCOUNT, connection.accountId)
        settings.set(KEY_DEVICE, connection.deviceId)
        settings.set(KEY_URL, connection.baseUrl.trimEnd('/'))
    }

    suspend fun clear() {
        settings.delete(KEY_URL)
        secrets.delete(KEY_TOKEN)
        listOf(KEY_TOKEN, KEY_ACCOUNT, KEY_DEVICE).forEach { settings.delete(it) }
    }

    private suspend fun token(): String? {
        secrets.get(KEY_TOKEN)?.let { return it }
        val legacy = settings.get(KEY_TOKEN) ?: return null
        secrets.set(KEY_TOKEN, legacy)
        settings.delete(KEY_TOKEN)
        return legacy
    }

    private companion object {
        const val KEY_URL = "hub.url"
        const val KEY_TOKEN = "hub.token"
        const val KEY_ACCOUNT = "hub.account"
        const val KEY_DEVICE = "hub.device"
    }
}
