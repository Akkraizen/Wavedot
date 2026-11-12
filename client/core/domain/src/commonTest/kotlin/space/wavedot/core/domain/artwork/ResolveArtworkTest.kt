package space.wavedot.core.domain.artwork

import kotlinx.coroutines.test.runTest
import space.wavedot.core.contract.ArtworkResolver
import space.wavedot.core.model.ArtworkReference
import space.wavedot.core.model.SourceId
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.TestRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveArtworkTest {
    private val source = SourceId("covers")
    private val registry = TestRegistry(
        FakeMediaSource(id = source, artwork = ArtworkResolver { key -> "https://covers.test/$key?token=secret" }),
        FakeMediaSource(id = SourceId("plain")),
    )
    private val resolve = ResolveArtwork(registry)

    @Test
    fun passesPlainUrlsThrough() = runTest {
        assertEquals("https://example.com/a.jpg", resolve("https://example.com/a.jpg"))
        assertEquals("/music/cover.jpg", resolve("/music/cover.jpg"))
    }

    @Test
    fun asksTheOwningSourceForReferences() = runTest {
        assertEquals("https://covers.test/al-1?token=secret", resolve(ArtworkReference.of(source, "al-1")))
    }

    @Test
    fun yieldsNothingWhenTheSourceCannotResolve() = runTest {
        assertNull(resolve(ArtworkReference.of(SourceId("missing"), "x")))
        assertNull(resolve(ArtworkReference.of(SourceId("plain"), "x")))
        assertNull(resolve("wavedot-artwork:broken"))
    }
}
