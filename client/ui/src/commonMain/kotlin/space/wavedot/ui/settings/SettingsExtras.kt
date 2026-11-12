package space.wavedot.ui.settings

import androidx.compose.runtime.Composable
import space.wavedot.ui.designsystem.color.AccentColor

class SettingsExtras(
    val accents: List<AccentColor> = emptyList(),
    val pluginPage: (pluginId: String) -> (@Composable () -> Unit)? = { null },
)
