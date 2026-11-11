package space.wavedot.source.local

import java.util.logging.Level
import java.util.logging.Logger

internal object TagLibraryLogging {
    private val logger: Logger = Logger.getLogger("org.jaudiotagger")

    fun quiet() {
        logger.level = Level.WARNING
    }
}
