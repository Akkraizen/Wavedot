package space.wavedot.core.testing

import kotlinx.coroutines.test.runTest
import space.wavedot.core.contract.MediaSource
import space.wavedot.core.contract.SearchQuery
import space.wavedot.core.model.AudioQuality
import space.wavedot.core.model.MediaId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class MediaSourceContractTest {
    protected abstract suspend fun createSource(): MediaSource

    protected abstract val knownTrackId: MediaId

    protected open val searchText: String? = null

    @Test
    fun descriptorIsConsistent() = runTest {
        val source = createSource()
        assertTrue(source.descriptor.displayName.isNotBlank())
        assertEquals(source.descriptor.id, knownTrackId.source)
    }

    @Test
    fun knownTrackResolvesThroughCatalog() = runTest {
        val source = createSource()
        val catalog = assertNotNull(source.catalog, "Every source must expose a catalog")
        val track = assertNotNull(catalog.tracks.get(knownTrackId))
        assertEquals(knownTrackId, track.id)
        assertTrue(track.title.isNotBlank())
    }

    @Test
    fun searchReturnsEntitiesOwnedBySource() = runTest {
        val text = searchText ?: return@runTest
        val source = createSource()
        val search = source.search ?: return@runTest
        val page = search.tracks.page(SearchQuery(text), limit = 10)
        assertTrue(page.items.isNotEmpty(), "Search for '$text' returned nothing")
        assertTrue(page.items.all { it.id.source == source.descriptor.id })
        assertTrue(page.items.size <= 10)
    }

    @Test
    fun streamingResolvesKnownTrack() = runTest {
        val source = createSource()
        val streaming = source.streaming ?: return@runTest
        val quality = streaming.supportedQualities.maxOrNull() ?: AudioQuality.NORMAL
        assertNotNull(streaming.resolve(knownTrackId, quality))
    }
}
