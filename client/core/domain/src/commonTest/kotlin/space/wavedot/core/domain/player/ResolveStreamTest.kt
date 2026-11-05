package space.wavedot.core.domain.player

import space.wavedot.core.model.AudioQuality
import space.wavedot.core.testing.TestRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveStreamTest {
    private val resolver = ResolveStream(TestRegistry())

    @Test
    fun picksBestSupportedQualityNotAboveRequested() {
        val supported = setOf(AudioQuality.LOW, AudioQuality.NORMAL)
        assertEquals(AudioQuality.NORMAL, resolver.pickQuality(AudioQuality.LOSSLESS, supported))
        assertEquals(AudioQuality.LOW, resolver.pickQuality(AudioQuality.LOW, supported))
    }

    @Test
    fun fallsBackToLowestWhenNothingIsLowerOrEqual() {
        assertEquals(AudioQuality.HIGH, resolver.pickQuality(AudioQuality.LOW, setOf(AudioQuality.HIGH, AudioQuality.LOSSLESS)))
    }
}
