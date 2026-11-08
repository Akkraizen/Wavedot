package space.wavedot.plugin.lrclib

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import space.wavedot.core.common.WavedotError
import space.wavedot.core.common.WavedotException
import space.wavedot.core.contract.LyricsProvider
import space.wavedot.core.contract.LyricsRequest
import space.wavedot.core.model.Track
import space.wavedot.core.model.lyrics.LrcParser
import space.wavedot.core.model.lyrics.Lyrics
import kotlin.math.abs

@Serializable
internal data class LrclibRecord(
    val id: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

class LrclibLyricsProvider(
    private val http: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : LyricsProvider {
    override val id: String = "lrclib"
    override val displayName: String = "LRCLIB"

    override suspend fun find(request: LyricsRequest): Lyrics? {
        val track = request.track
        val artist = track.artists.firstOrNull()?.name ?: return null
        val title = track.title.trim()
        val record = exact(track, title, artist)
            ?: TitleNormalizer.clean(title).takeIf { it != title }?.let { exact(track, it, artist) }
            ?: search(track, TitleNormalizer.clean(title), artist)
        return record?.toLyrics()
    }

    private suspend fun exact(track: Track, title: String, artist: String): LrclibRecord? {
        val response = http.get("$baseUrl/api/get") {
            parameter("track_name", title)
            parameter("artist_name", artist)
            track.album?.title?.let { parameter("album_name", it) }
            track.duration?.let { parameter("duration", it.inWholeSeconds) }
        }
        if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.BadRequest) return null
        return json.decodeFromString<LrclibRecord>(response.checked())
    }

    private suspend fun search(track: Track, title: String, artist: String): LrclibRecord? {
        val response = http.get("$baseUrl/api/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
        }
        val candidates = json.decodeFromString<List<LrclibRecord>>(response.checked())
        val seconds = track.duration?.inWholeSeconds?.toDouble()
        return candidates
            .filter { candidate -> seconds == null || candidate.duration == null || abs(candidate.duration - seconds) <= DURATION_TOLERANCE }
            .sortedByDescending { if (it.syncedLyrics.isNullOrBlank()) 0 else 1 }
            .firstOrNull { it.instrumental || !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
    }

    private fun LrclibRecord.toLyrics(): Lyrics? = when {
        instrumental -> Lyrics.plain(INSTRUMENTAL, displayName)
        !syncedLyrics.isNullOrBlank() -> LrcParser.parse(syncedLyrics, displayName)
        !plainLyrics.isNullOrBlank() -> Lyrics.plain(plainLyrics, displayName)
        else -> null
    }

    private suspend fun HttpResponse.checked(): String {
        val text = bodyAsText()
        if (!status.isSuccess()) throw WavedotException(WavedotError.Network("LRCLIB responded ${status.value}"))
        return text
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://lrclib.net"
        internal const val INSTRUMENTAL: String = "♪ Instrumental ♪"
        private const val DURATION_TOLERANCE = 3.0
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}

internal object TitleNormalizer {
    private val DECORATIONS = Regex(
        """\s*[(\[](?:[^)\]]*?(?:remaster|version|edit|live|mono|stereo|mix|feat\.?|ft\.|deluxe|bonus)[^)\]]*)[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val DASH_SUFFIX = Regex("""\s+-\s+(?:.*?(?:remaster|version|edit|live|mix)).*$""", RegexOption.IGNORE_CASE)

    fun clean(title: String): String = title.replace(DECORATIONS, "").replace(DASH_SUFFIX, "").trim().ifEmpty { title }
}
