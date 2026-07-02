package dev.mlg.quedalle.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** The launcher's fixed dark palette; accents come from the Material scheme. */
object QuedalleColors {
    val Background   = Color(0xFF000000)
    val CardIdle     = Color(0xFF141414)
    val Surface      = Color(0xFF161616)
    val SurfaceDim   = Color(0xFF0A0A0A)
    val FieldFocused = Color(0xFF0E0E0E)
    val BorderFocused = Color(0xFF2A2A2A)
    val BorderIdle   = Color(0xFF1E1E1E)
    val TextPrimary  = Color(0xFFE0E0E0)
    val TextMuted    = Color(0xFF555555)
    val TextFaint    = Color(0xFF333333)
    val TextDisabled = Color(0xFF333333)
    val Danger       = Color(0xFFCC4444)
    val CheckerDark  = Color(0xFF2A2A2A)
    val CheckerLight = Color(0xFF555555)

    /** Default divider line color. */
    const val DividerDefault = 0xFF2A2A2A.toInt()
    /** Same color as app cards, usable for spacers. */
    const val TileAppColor = 0xFF141414.toInt()
    /** Fully transparent spacer. */
    const val TileTransparent = 0x00000000

    /** 12 preset tile colors (3 rows × 4 columns in the picker). */
    val TilePresets: List<Int> = listOf(
        0xFF1C2B3A.toInt(), 0xFF2B1C3A.toInt(), 0xFF1C3A2B.toInt(), 0xFF3A2B1C.toInt(),
        0xFF3A1C1C.toInt(), 0xFF1C3A3A.toInt(), 0xFF2B3A1C.toInt(), 0xFF3A1C2B.toInt(),
        0xFF0F4C81.toInt(), 0xFF6B1F1F.toInt(), 0xFF1A5C40.toInt(), 0xFF7A5A10.toInt(),
    )
}

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        darkColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
