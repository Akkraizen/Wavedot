package space.wavedot.ui.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WavedotMark(modifier: Modifier = Modifier, logoSize: Dp = 20.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        WavedotLogo(logoSize)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "wavedot",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
