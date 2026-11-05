package space.wavedot.app.shell

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.wavedot.core.domain.library.LocalLibrary
import space.wavedot.core.model.Track
import space.wavedot.source.local.LocalMediaSource

class LocalLibraryAdapter(private val source: LocalMediaSource) : LocalLibrary {
    override val tracks: Flow<List<Track>> = source.tracks.map { source.allTracks() }

    override suspend fun rescan(): Int = source.refresh()
}
