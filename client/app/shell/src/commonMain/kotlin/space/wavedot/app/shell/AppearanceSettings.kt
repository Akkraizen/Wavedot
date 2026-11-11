package space.wavedot.app.shell

import kotlinx.coroutines.flow.Flow
import space.wavedot.core.common.KeyValueStore

class AppearanceSettings(private val store: KeyValueStore) {
    val accentId: Flow<String?> = store.observe(KEY_ACCENT)

    suspend fun setAccent(id: String) = store.set(KEY_ACCENT, id)

    private companion object {
        const val KEY_ACCENT = "ui.accent"
    }
}
