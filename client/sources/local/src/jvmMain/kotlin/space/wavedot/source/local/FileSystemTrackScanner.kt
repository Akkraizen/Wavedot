package space.wavedot.source.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Artwork
import space.wavedot.core.model.Track
import java.io.File
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

class FileSystemTrackScanner(
    private val coverCache: File? = null,
    private val roots: suspend () -> List<File>,
) : LocalTrackScanner {
    init {
        TagLibraryLogging.quiet()
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
            val cover = runCatching { tag?.firstArtwork?.binaryData }.getOrNull()
                ?.let { storeCover(file, album?.title, it) }
                ?: folderCover(file)
            fallback.copy(title = title, artists = artists, album = album, duration = length, artwork = cover)
        }.getOrDefault(fallback.copy(artwork = folderCover(file)))
        return LocalTrack(track, path, MIME_TYPES[file.extension.lowercase()])
    }

    private fun storeCover(file: File, album: String?, data: ByteArray): Artwork? {
        val cache = coverCache ?: return null
        if (data.isEmpty()) return null
        val key = sha1(album?.let { "${file.parentFile?.absolutePath}|$it" } ?: file.absolutePath)
        val target = File(cache, "$key.img")
        if (!target.exists()) {
            cache.mkdirs()
            target.writeBytes(data)
        }
        return Artwork(target.absolutePath)
    }

    private fun folderCover(file: File): Artwork? =
        COVER_NAMES.asSequence()
            .map { File(file.parentFile, it) }
            .firstOrNull { it.isFile }
            ?.let { Artwork(it.absolutePath) }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val COVER_NAMES = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png")
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
