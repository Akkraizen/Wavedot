package space.wavedot.ui.designsystem.color

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import java.util.concurrent.TimeUnit

internal object WindowsAccent {
    val color: Color? by lazy { read() }

    internal fun parse(output: String): Color? {
        val value = Regex("""AccentColor\s+REG_DWORD\s+0x([0-9a-fA-F]+)""").find(output)?.groupValues?.get(1) ?: return null
        val abgr = value.toLong(16)
        val red = (abgr and 0xFF).toInt()
        val green = ((abgr shr 8) and 0xFF).toInt()
        val blue = ((abgr shr 16) and 0xFF).toInt()
        return Color(red, green, blue)
    }

    private fun read(): Color? {
        if (!System.getProperty("os.name").lowercase().contains("win")) return null
        return runCatching {
            val process = ProcessBuilder("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\DWM", "/v", "AccentColor")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy()
                return null
            }
            parse(process.inputStream.bufferedReader().readText())
        }.getOrNull()
    }
}

actual fun isSystemAccentSupported(): Boolean = WindowsAccent.color != null

@Composable
actual fun systemColorScheme(dark: Boolean): ColorScheme? {
    val accent = WindowsAccent.color ?: return null
    return remember(accent, dark) { schemeFromSeed(accent, dark) }
}
