package space.wavedot.app.shell

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.wavedot.core.common.KeyValueStore
import space.wavedot.ui.designsystem.color.Accents

class AppearanceSettings(private val store: KeyValueStore) {
    val accentId: Flow<String> = store.observe(KEY_ACCENT).map { Accents.byId(it).id }

    suspend fun setAccent(id: String) = store.set(KEY_ACCENT, Accents.byId(id).id)

    private companion object {
        const val KEY_ACCENT = "ui.accent"
    }
}
