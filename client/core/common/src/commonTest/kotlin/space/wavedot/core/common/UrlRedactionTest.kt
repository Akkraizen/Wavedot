package space.wavedot.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlRedactionTest {
    @Test
    fun dropsQueryFragmentAndUserInfo() {
        assertEquals(
            "https://music.test/rest/stream.view",
            "https://igor:pw@music.test/rest/stream.view?u=igor&t=abc&s=salt#frag".withoutUrlSecrets(),
        )
    }

    @Test
    fun keepsPlainUrlsAndPaths() {
        assertEquals("http://127.0.0.1:8080/stream/1", "http://127.0.0.1:8080/stream/1".withoutUrlSecrets())
        assertEquals("/music/a@b.mp3", "/music/a@b.mp3".withoutUrlSecrets())
        assertEquals("https://host", "https://host?x=1".withoutUrlSecrets())
    }
}
