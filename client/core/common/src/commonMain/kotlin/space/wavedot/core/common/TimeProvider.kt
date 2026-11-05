package space.wavedot.core.common

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun interface TimeProvider {
    fun nowMillis(): Long
}

@OptIn(ExperimentalTime::class)
object SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
