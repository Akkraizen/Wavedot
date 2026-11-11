package space.wavedot.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.wavedot.ui.designsystem.color.AccentColor
import space.wavedot.ui.designsystem.color.AccentKind
import space.wavedot.ui.designsystem.color.Accents
import space.wavedot.ui.designsystem.color.schemeFromSeed
import space.wavedot.ui.designsystem.color.systemColorScheme

val WavedotDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFCFBDFF),
    onPrimary = Color(0xFF35208A),
    primaryContainer = Color(0xFF5B40D6),
    onPrimaryContainer = Color(0xFFEBE2FF),
    inversePrimary = Color(0xFF6247D6),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFF7BDACD),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005046),
    onTertiaryContainer = Color(0xFF97F6E8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceTint = Color(0xFFCFBDFF),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B383E),
    surfaceDim = Color(0xFF141218),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
)

val WavedotLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF6247D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DDFF),
    onPrimaryContainer = Color(0xFF22005D),
    inversePrimary = Color(0xFFCFBDFF),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF006B5F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9EF2E3),
    onTertiaryContainer = Color(0xFF00201C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceTint = Color(0xFF6247D6),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFDF7FF),
    surfaceDim = Color(0xFFDED8E1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
)

val WavedotShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun TextStyle.emphasized(weight: FontWeight, tracking: Float? = null): TextStyle =
    copy(fontWeight = weight, letterSpacing = tracking?.sp ?: letterSpacing)

val WavedotTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.emphasized(FontWeight.Bold, -0.5f),
        displayMedium = base.displayMedium.emphasized(FontWeight.Bold, -0.25f),
        displaySmall = base.displaySmall.emphasized(FontWeight.Bold),
        headlineLarge = base.headlineLarge.emphasized(FontWeight.Bold),
        headlineMedium = base.headlineMedium.emphasized(FontWeight.Bold),
        headlineSmall = base.headlineSmall.emphasized(FontWeight.SemiBold),
        titleLarge = base.titleLarge.emphasized(FontWeight.SemiBold),
        titleMedium = base.titleMedium.emphasized(FontWeight.SemiBold),
        labelLarge = base.labelLarge.emphasized(FontWeight.SemiBold),
    )
}

@Composable
fun rememberAccentScheme(accent: AccentColor, darkTheme: Boolean): ColorScheme {
    val brand = if (darkTheme) WavedotDarkColors else WavedotLightColors
    return when (val kind = accent.kind) {
        AccentKind.Brand -> brand
        AccentKind.System -> systemColorScheme(darkTheme) ?: brand
        is AccentKind.Seed -> remember(kind, darkTheme) { schemeFromSeed(kind.color, darkTheme, kind.spec) }
    }
}

@Composable
fun WavedotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = Accents.Default,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = rememberAccentScheme(accent, darkTheme).animated(),
        motionScheme = MotionScheme.expressive(),
        shapes = WavedotShapes,
        typography = WavedotTypography,
        content = content,
    )
}

@Composable
private fun Color.animatedTo(): Color {
    val value by animateColorAsState(this, MaterialTheme.motionScheme.slowEffectsSpec())
    return value
}

@Composable
private fun ColorScheme.animated(): ColorScheme = copy(
    primary = primary.animatedTo(),
    onPrimary = onPrimary.animatedTo(),
    primaryContainer = primaryContainer.animatedTo(),
    onPrimaryContainer = onPrimaryContainer.animatedTo(),
    inversePrimary = inversePrimary.animatedTo(),
    secondary = secondary.animatedTo(),
    onSecondary = onSecondary.animatedTo(),
    secondaryContainer = secondaryContainer.animatedTo(),
    onSecondaryContainer = onSecondaryContainer.animatedTo(),
    tertiary = tertiary.animatedTo(),
    onTertiary = onTertiary.animatedTo(),
    tertiaryContainer = tertiaryContainer.animatedTo(),
    onTertiaryContainer = onTertiaryContainer.animatedTo(),
    background = background.animatedTo(),
    onBackground = onBackground.animatedTo(),
    surface = surface.animatedTo(),
    onSurface = onSurface.animatedTo(),
    surfaceVariant = surfaceVariant.animatedTo(),
    onSurfaceVariant = onSurfaceVariant.animatedTo(),
    surfaceTint = surfaceTint.animatedTo(),
    outline = outline.animatedTo(),
    outlineVariant = outlineVariant.animatedTo(),
    surfaceBright = surfaceBright.animatedTo(),
    surfaceDim = surfaceDim.animatedTo(),
    surfaceContainerLowest = surfaceContainerLowest.animatedTo(),
    surfaceContainerLow = surfaceContainerLow.animatedTo(),
    surfaceContainer = surfaceContainer.animatedTo(),
    surfaceContainerHigh = surfaceContainerHigh.animatedTo(),
    surfaceContainerHighest = surfaceContainerHighest.animatedTo(),
)
