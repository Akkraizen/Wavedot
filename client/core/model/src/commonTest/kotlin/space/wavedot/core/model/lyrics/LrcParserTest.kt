package space.wavedot.core.model.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LrcParserTest {
    private val lrc = """
        [ar:Kino]
        [ti:Kukushka]
        [00:12.50]Песен ещё ненаписанных сколько
        [00:17.3]Скажи, кукушка
        [00:21.123][01:05.00]Пропой
        [00:25.00]
    """.trimIndent()

    @Test
    fun parsesTimestampsInOrder() {
        val lyrics = LrcParser.parse(lrc, "test")
        assertTrue(lyrics.synced)
        assertEquals(
            listOf(12_500L, 17_300L, 21_123L, 25_000L, 65_000L),
            lyrics.lines.map { it.start!!.inWholeMilliseconds },
        )
        assertEquals("Скажи, кукушка", lyrics.lines[1].text)
        assertEquals("Пропой", lyrics.lines.last().text)
    }

    @Test
    fun appliesOffsetTag() {
        val lyrics = LrcParser.parse("[offset:+500]\n[00:10.00]Line", "test")
        assertEquals(9_500.milliseconds, lyrics.lines.single().start)
    }

    @Test
    fun fallsBackToPlainText() {
        val lyrics = LrcParser.parse("\n[ar:Kino]\nFirst line\n\nSecond line\n", "test")
        assertFalse(lyrics.synced)
        assertEquals(listOf("First line", "", "Second line"), lyrics.lines.map { it.text })
    }

    @Test
    fun findsActiveLine() {
        val lyrics = LrcParser.parse(lrc, "test")
        assertEquals(-1, lyrics.activeLineIndex(5.seconds))
        assertEquals(0, lyrics.activeLineIndex(12.5.seconds))
        assertEquals(1, lyrics.activeLineIndex(20.seconds))
        assertEquals(4, lyrics.activeLineIndex(10_000.seconds))
        assertEquals(-1, Lyrics.plain("text", "test").activeLineIndex(20.seconds))
    }
}
