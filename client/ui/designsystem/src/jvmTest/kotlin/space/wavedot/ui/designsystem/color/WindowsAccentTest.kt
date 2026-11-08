package space.wavedot.ui.designsystem.color

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsAccentTest {
    @Test
    fun parsesAbgrRegistryValue() {
        val output = "\nHKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\DWM\n    AccentColor    REG_DWORD    0xff9e5a00\n"
        assertEquals(Color(0x00, 0x5A, 0x9E), WindowsAccent.parse(output))
    }

    @Test
    fun missingValueYieldsNull() {
        assertNull(WindowsAccent.parse("ERROR: The system was unable to find the specified registry key or value."))
    }
}
