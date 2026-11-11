package space.wavedot.ui.designsystem.color

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedSchemeTest {
    @Test
    fun labRoundTripPreservesColor() {
        val seed = Color(0xFF2F6FED)
        val back = ColorSpace.fromLch(ColorSpace.toLch(seed))!!
        assertEquals(ColorSpace.toHex(seed), ColorSpace.toHex(back))
    }

    @Test
    fun tonesMatchRequestedLightness() {
        val palette = TonalPalette(hue = 280.0, chroma = 70.0)
        listOf(10, 30, 40, 50, 80, 90).forEach { tone ->
            val actual = ColorSpace.toLch(palette.tone(tone)).lightness
            assertTrue(abs(actual - tone) < 1.0, "tone $tone produced lightness $actual")
        }
        assertEquals(Color.White, palette.tone(100))
        assertEquals(Color.Black, palette.tone(0))
    }

    @Test
    fun generatedSchemesKeepReadableContrast() {
        val seeds = Accents.presets.mapNotNull { (it.kind as? AccentKind.Seed)?.let { kind -> kind.color to kind.spec } }
        seeds.forEach { (seed, spec) ->
            listOf(false, true).forEach { dark ->
                val scheme = schemeFromSeed(seed, dark, spec)
                val pairs = listOf(
                    scheme.primary to scheme.onPrimary,
                    scheme.primaryContainer to scheme.onPrimaryContainer,
                    scheme.surface to scheme.onSurface,
                    scheme.secondaryContainer to scheme.onSecondaryContainer,
                    scheme.tertiaryContainer to scheme.onTertiaryContainer,
                )
                pairs.forEach { (background, foreground) ->
                    val ratio = ColorSpace.contrast(background, foreground)
                    assertTrue(ratio >= 4.5, "${ColorSpace.toHex(seed)} dark=$dark contrast $ratio")
                }
            }
        }
    }

    @Test
    fun primaryFollowsSeedHue() {
        val seed = Color(0xFF00A896)
        val primary = schemeFromSeed(seed, dark = false).primary
        val delta = abs(ColorSpace.toLch(primary).hue - ColorSpace.toLch(seed).hue)
        assertTrue(minOf(delta, 360 - delta) < 8, "hue drifted by $delta")
    }

    @Test
    fun unknownAccentFallsBackToDefault() {
        assertEquals(Accents.Default, Accents.byId("nope"))
        assertEquals("ocean", Accents.byId("ocean").id)
        assertTrue(Accents.available(systemSupported = true).first() == Accents.System)
        assertTrue(Accents.System !in Accents.available(systemSupported = false))
    }
}
