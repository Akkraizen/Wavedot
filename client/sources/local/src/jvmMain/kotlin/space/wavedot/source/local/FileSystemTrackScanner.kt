package space.wavedot.source.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Track
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

class FileSystemTrackScanner(
    private val roots: suspend () -> List<File>,
) : LocalTrackScanner {
    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    override suspend fun scan(): List<LocalTrack> = withContext(Dispatchers.IO) {
        roots()
            .filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension.lowercase() in EXTENSIONS }.toList() }
            .distinctBy { it.canonicalPath }
            .map(::readTrack)
    }

    private fun readTrack(file: File): LocalTrack {
        val path = file.absolutePath
        val fallback = Track(id = LocalMediaSource.idFor(path), title = file.nameWithoutExtension)
        val track = runCatching {
            val audio = AudioFileIO.read(file)
            val tag = audio.tag
            val title = tag?.getFirst(FieldKey.TITLE).orEmpty().ifBlank { file.nameWithoutExtension }
            val artists = tag?.getAll(FieldKey.ARTIST).orEmpty().filter { it.isNotBlank() }.map { ArtistRef(null, it) }
            val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }?.let { AlbumRef(null, it) }
            val length = audio.audioHeader?.trackLength?.takeIf { it > 0 }?.seconds
            fallback.copy(title = title, artists = artists, album = album, duration = length)
        }.getOrDefault(fallback)
        return LocalTrack(track, path, MIME_TYPES[file.extension.lowercase()])
    }

    companion object {
        private val MIME_TYPES = mapOf(
            "mp3" to "audio/mpeg",
            "flac" to "audio/flac",
            "m4a" to "audio/mp4",
            "aac" to "audio/aac",
            "ogg" to "audio/ogg",
            "opus" to "audio/opus",
            "wav" to "audio/wav",
        )
        val EXTENSIONS: Set<String> = MIME_TYPES.keys
    }
}
