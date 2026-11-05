package space.wavedot.app.shell

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.wavedot.core.common.KeyValueStore

class MusicFolders(private val store: KeyValueStore) {
    val folders: Flow<List<String>> = store.observe(KEY).map(::decode)

    suspend fun current(): List<String> = decode(store.get(KEY))

    suspend fun set(folders: List<String>) = store.set(KEY, folders.distinct().joinToString("\n").ifEmpty { null })

    private fun decode(value: String?): List<String> = value?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val KEY = "local.folders"
    }
}
