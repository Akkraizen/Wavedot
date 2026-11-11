package space.wavedot.ui.designsystem.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class SchemeSpec(
    val primaryChroma: Double? = null,
    val secondaryChroma: Double = 16.0,
    val tertiaryChroma: Double = 32.0,
    val tertiaryHueShift: Double = 60.0,
    val neutralChroma: Double = 4.0,
    val neutralVariantChroma: Double = 8.0,
)

class SeedPalettes(seed: Color, spec: SchemeSpec = SchemeSpec()) {
    private val lch = ColorSpace.toLch(seed)

    val primary = TonalPalette(lch.hue, spec.primaryChroma ?: maxOf(lch.chroma, MIN_PRIMARY_CHROMA))
    val secondary = TonalPalette(lch.hue, spec.secondaryChroma)
    val tertiary = TonalPalette((lch.hue + spec.tertiaryHueShift) % 360, spec.tertiaryChroma)
    val neutral = TonalPalette(lch.hue, spec.neutralChroma)
    val neutralVariant = TonalPalette(lch.hue, spec.neutralVariantChroma)
    val error = TonalPalette(ERROR_HUE, ERROR_CHROMA)

    private companion object {
        const val MIN_PRIMARY_CHROMA = 48.0
        const val ERROR_HUE = 33.0
        const val ERROR_CHROMA = 80.0
    }
}

fun schemeFromSeed(seed: Color, dark: Boolean, spec: SchemeSpec = SchemeSpec()): ColorScheme {
    val p = SeedPalettes(seed, spec)
    return if (dark) {
        darkColorScheme(
            primary = p.primary.tone(80),
            onPrimary = p.primary.tone(20),
            primaryContainer = p.primary.tone(30),
            onPrimaryContainer = p.primary.tone(90),
            inversePrimary = p.primary.tone(40),
            secondary = p.secondary.tone(80),
            onSecondary = p.secondary.tone(20),
            secondaryContainer = p.secondary.tone(30),
            onSecondaryContainer = p.secondary.tone(90),
            tertiary = p.tertiary.tone(80),
            onTertiary = p.tertiary.tone(20),
            tertiaryContainer = p.tertiary.tone(30),
            onTertiaryContainer = p.tertiary.tone(90),
            error = p.error.tone(80),
            onError = p.error.tone(20),
            errorContainer = p.error.tone(30),
            onErrorContainer = p.error.tone(90),
            background = p.neutral.tone(6),
            onBackground = p.neutral.tone(90),
            surface = p.neutral.tone(6),
            onSurface = p.neutral.tone(90),
            surfaceVariant = p.neutralVariant.tone(30),
            onSurfaceVariant = p.neutralVariant.tone(80),
            surfaceTint = p.primary.tone(80),
            inverseSurface = p.neutral.tone(90),
            inverseOnSurface = p.neutral.tone(20),
            outline = p.neutralVariant.tone(60),
            outlineVariant = p.neutralVariant.tone(30),
            scrim = Color.Black,
            surfaceBright = p.neutral.tone(24),
            surfaceDim = p.neutral.tone(6),
            surfaceContainerLowest = p.neutral.tone(4),
            surfaceContainerLow = p.neutral.tone(10),
            surfaceContainer = p.neutral.tone(12),
            surfaceContainerHigh = p.neutral.tone(17),
            surfaceContainerHighest = p.neutral.tone(22),
        )
    } else {
        lightColorScheme(
            primary = p.primary.tone(40),
            onPrimary = p.primary.tone(100),
            primaryContainer = p.primary.tone(90),
            onPrimaryContainer = p.primary.tone(10),
            inversePrimary = p.primary.tone(80),
            secondary = p.secondary.tone(40),
            onSecondary = p.secondary.tone(100),
            secondaryContainer = p.secondary.tone(90),
            onSecondaryContainer = p.secondary.tone(10),
            tertiary = p.tertiary.tone(40),
            onTertiary = p.tertiary.tone(100),
            tertiaryContainer = p.tertiary.tone(90),
            onTertiaryContainer = p.tertiary.tone(10),
            error = p.error.tone(40),
            onError = p.error.tone(100),
            errorContainer = p.error.tone(90),
            onErrorContainer = p.error.tone(10),
            background = p.neutral.tone(98),
            onBackground = p.neutral.tone(10),
            surface = p.neutral.tone(98),
            onSurface = p.neutral.tone(10),
            surfaceVariant = p.neutralVariant.tone(90),
            onSurfaceVariant = p.neutralVariant.tone(30),
            surfaceTint = p.primary.tone(40),
            inverseSurface = p.neutral.tone(20),
            inverseOnSurface = p.neutral.tone(95),
            outline = p.neutralVariant.tone(50),
            outlineVariant = p.neutralVariant.tone(80),
            scrim = Color.Black,
            surfaceBright = p.neutral.tone(98),
            surfaceDim = p.neutral.tone(87),
            surfaceContainerLowest = p.neutral.tone(100),
            surfaceContainerLow = p.neutral.tone(96),
            surfaceContainer = p.neutral.tone(94),
            surfaceContainerHigh = p.neutral.tone(92),
            surfaceContainerHighest = p.neutral.tone(90),
        )
    }
}
