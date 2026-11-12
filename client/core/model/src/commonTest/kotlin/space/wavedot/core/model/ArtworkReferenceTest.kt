package space.wavedot.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkReferenceTest {
    @Test
    fun roundTripsSourceAndKey() {
        val reference = ArtworkReference.of(SourceId("subsonic"), "al-1:big")
        assertTrue(ArtworkReference.matches(reference))
        assertEquals(MediaId(SourceId("subsonic"), "al-1:big"), ArtworkReference.parse(reference))
    }

    @Test
    fun ignoresPlainUrlsAndBrokenReferences() {
        assertFalse(ArtworkReference.matches("https://example.com/a.jpg"))
        assertNull(ArtworkReference.parse("https://example.com/a.jpg"))
        assertNull(ArtworkReference.parse("wavedot-artwork:nokey"))
    }
}
