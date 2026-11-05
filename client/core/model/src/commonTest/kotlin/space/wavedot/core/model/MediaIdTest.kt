package space.wavedot.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaIdTest {
    @Test
    fun parsesAndFormatsRoundTrip() {
        val id = MediaId.parse("yandex-music:12345:678")
        assertEquals(SourceId("yandex-music"), id.source)
        assertEquals("12345:678", id.key)
        assertEquals("yandex-music:12345:678", id.toString())
    }

    @Test
    fun rejectsMalformedValues() {
        assertFailsWith<IllegalArgumentException> { MediaId.parse("no-separator") }
        assertFailsWith<IllegalArgumentException> { MediaId.parse(":key") }
        assertFailsWith<IllegalArgumentException> { MediaId.parse("source:") }
    }
}
