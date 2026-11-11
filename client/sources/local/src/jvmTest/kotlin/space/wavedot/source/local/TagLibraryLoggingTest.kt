package space.wavedot.source.local

import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TagLibraryLoggingTest {
    @Test
    fun keepsTagLibraryQuietAfterGarbageCollection() {
        FileSystemTrackScanner { emptyList() }
        repeat(3) {
            System.gc()
            Thread.sleep(20)
        }
        val logger = Logger.getLogger("org.jaudiotagger")
        assertEquals(Level.WARNING, logger.level)
        assertFalse(Logger.getLogger("org.jaudiotagger.tag.id3").isLoggable(Level.INFO))
    }
}
