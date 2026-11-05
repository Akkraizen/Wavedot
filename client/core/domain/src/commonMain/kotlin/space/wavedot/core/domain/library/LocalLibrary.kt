package space.wavedot.core.domain.library

import kotlinx.coroutines.flow.Flow
import space.wavedot.core.model.Track

interface LocalLibrary {
    val tracks: Flow<List<Track>>

    suspend fun rescan(): Int
}
