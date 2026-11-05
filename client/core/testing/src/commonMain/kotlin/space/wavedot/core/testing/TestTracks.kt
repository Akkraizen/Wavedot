package space.wavedot.core.testing

import space.wavedot.core.model.ArtistRef
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.SourceId
import space.wavedot.core.model.Track
import kotlin.time.Duration.Companion.seconds

object TestTracks {
    val SOURCE: SourceId = SourceId("test")

    fun track(key: String, title: String = "Track $key", source: SourceId = SOURCE): Track = Track(
        id = MediaId(source, key),
        title = title,
        artists = listOf(ArtistRef(null, "Artist $key")),
        duration = 180.seconds,
    )

    fun tracks(count: Int, source: SourceId = SOURCE): List<Track> = (1..count).map { track("$it", source = source) }
}
