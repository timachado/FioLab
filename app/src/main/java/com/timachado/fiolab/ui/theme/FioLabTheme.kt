package com.timachado.fiolab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FioBackground = Color(0xFF08131D)
val FioSurface = Color(0xFF101D28)
val FioSurfaceAlt = Color(0xFF172632)
val FioGold = Color(0xFFE6BE70)
val FioGoldSoft = Color(0xFFF6E7C4)
val FioText = Color(0xFFF7F4EE)
val FioTextMuted = Color(0xFFA9B3BC)
val FioDanger = Color(0xFFFF8A80)

private val colors = darkColorScheme(
    primary = FioGold,
    onPrimary = Color(0xFF241704),
    secondary = FioGoldSoft,
    background = FioBackground,
    onBackground = FioText,
    surface = FioSurface,
    onSurface = FioText,
    surfaceVariant = FioSurfaceAlt,
    onSurfaceVariant = FioTextMuted,
    error = FioDanger
)

@Composable
fun FioLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
