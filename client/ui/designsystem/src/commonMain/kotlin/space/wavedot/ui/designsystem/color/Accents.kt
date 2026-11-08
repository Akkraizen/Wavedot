package space.wavedot.ui.designsystem.color

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

sealed interface AccentKind {
    data object Brand : AccentKind
    data object System : AccentKind
    data class Seed(val color: Color, val spec: SchemeSpec = SchemeSpec()) : AccentKind
}

data class AccentColor(
    val id: String,
    val name: String,
    val kind: AccentKind,
    val swatch: Color,
)

object Accents {
    val Wavedot = AccentColor("wavedot", "Wavedot", AccentKind.Brand, Color(0xFF6247D6))
    val System = AccentColor("system", "System", AccentKind.System, Color(0xFF8E8A99))

    val presets: List<AccentColor> = listOf(
        Wavedot,
        seed("ocean", "Ocean", 0xFF2F6FED),
        seed("teal", "Teal", 0xFF00A896),
        seed("forest", "Forest", 0xFF3A9A3A),
        seed("amber", "Amber", 0xFFE8A317),
        seed("coral", "Coral", 0xFFF2545B),
        seed("rose", "Rose", 0xFFE0457B),
        AccentColor(
            id = "mono",
            name = "Mono",
            kind = AccentKind.Seed(Color(0xFF808080), SchemeSpec(primaryChroma = 0.0, secondaryChroma = 0.0, tertiaryChroma = 0.0, neutralChroma = 0.0, neutralVariantChroma = 0.0)),
            swatch = Color(0xFF3A3A3A),
        ),
    )

    val Default: AccentColor = Wavedot

    fun available(systemSupported: Boolean): List<AccentColor> =
        if (systemSupported) listOf(System) + presets else presets

    fun byId(id: String?): AccentColor = (presets + System).firstOrNull { it.id == id } ?: Default

    private fun seed(id: String, name: String, argb: Long): AccentColor =
        AccentColor(id, name, AccentKind.Seed(Color(argb)), Color(argb))
}

expect fun isSystemAccentSupported(): Boolean

@Composable
expect fun systemColorScheme(dark: Boolean): ColorScheme?
