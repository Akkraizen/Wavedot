package space.wavedot.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object WavedotColors {
    val Accent = Color(0xFF7C5CFF)
    val AccentDark = Color(0xFFB8A6FF)
    val Ink = Color(0xFF0F0E17)
    val Paper = Color(0xFFF7F6FB)
    val SurfaceDark = Color(0xFF18171F)
    val SurfaceLight = Color(0xFFFFFFFF)
}

private val DarkScheme = darkColorScheme(
    primary = WavedotColors.AccentDark,
    onPrimary = WavedotColors.Ink,
    background = WavedotColors.Ink,
    surface = WavedotColors.SurfaceDark,
    surfaceVariant = Color(0xFF24222E),
    onBackground = Color(0xFFECEAF4),
    onSurface = Color(0xFFECEAF4),
    onSurfaceVariant = Color(0xFFA9A6B8),
)

private val LightScheme = lightColorScheme(
    primary = WavedotColors.Accent,
    onPrimary = Color.White,
    background = WavedotColors.Paper,
    surface = WavedotColors.SurfaceLight,
    surfaceVariant = Color(0xFFECE9F6),
    onBackground = WavedotColors.Ink,
    onSurface = WavedotColors.Ink,
    onSurfaceVariant = Color(0xFF5E5A6E),
)

@Composable
fun WavedotTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),
        content = content,
    )
}
