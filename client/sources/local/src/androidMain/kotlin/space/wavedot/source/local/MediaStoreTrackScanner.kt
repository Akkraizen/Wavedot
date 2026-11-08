package space.wavedot.source.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.wavedot.core.model.AlbumRef
import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.Artwork
import space.wavedot.core.model.Track
import kotlin.time.Duration.Companion.milliseconds

class MediaStoreTrackScanner(private val context: Context) : LocalTrackScanner {
    private companion object {
        val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
    }

    override suspend fun scan(): List<LocalTrack> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        val result = mutableListOf<LocalTrack>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn)).toString()
                val artist = cursor.getString(artistColumn)?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                val track = Track(
                    id = LocalMediaSource.idFor(uri),
                    title = cursor.getString(titleColumn).orEmpty(),
                    artists = listOfNotNull(artist).map { ArtistRef(null, it) },
                    album = cursor.getString(albumColumn)?.let { AlbumRef(null, it) },
                    duration = cursor.getLong(durationColumn).takeIf { it > 0 }?.milliseconds,
                    artwork = Artwork(ContentUris.withAppendedId(ALBUM_ART, cursor.getLong(albumIdColumn)).toString()),
                )
                result += LocalTrack(track, uri, cursor.getString(mimeColumn))
            }
        }
        result
    }
}
