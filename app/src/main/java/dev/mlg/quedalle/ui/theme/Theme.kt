package dev.mlg.quedalle.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.mlg.quedalle.model.ThemeMode

/** Semantic colors of the launcher, resolved per light/dark theme. */
data class QuedallePalette(
    val background: Color,
    val card: Color,
    val surface: Color,
    val surfaceDim: Color,
    val fieldFocused: Color,
    val borderFocused: Color,
    val borderIdle: Color,
    val textStrong: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textFaint: Color,
    val textDisabled: Color,
    val danger: Color,
    val dividerLine: Color,
    val checkerA: Color,
    val checkerB: Color,
)

val DarkPalette = QuedallePalette(
    background    = Color(0xFF000000),
    card          = Color(0xFF141414),
    surface       = Color(0xFF161616),
    surfaceDim    = Color(0xFF0A0A0A),
    fieldFocused  = Color(0xFF0E0E0E),
    borderFocused = Color(0xFF2A2A2A),
    borderIdle    = Color(0xFF1E1E1E),
    textStrong    = Color(0xFFFFFFFF),
    textPrimary   = Color(0xFFE0E0E0),
    textMuted     = Color(0xFF555555),
    textFaint     = Color(0xFF333333),
    textDisabled  = Color(0xFF333333),
    danger        = Color(0xFFCC4444),
    dividerLine   = Color(0xFF2A2A2A),
    checkerA      = Color(0xFF2A2A2A),
    checkerB      = Color(0xFF555555),
)

val LightPalette = QuedallePalette(
    background    = Color(0xFFFAFAFA),
    card          = Color(0xFFEBEBEB),
    surface       = Color(0xFFF2F2F2),
    surfaceDim    = Color(0xFFF0F0F0),
    fieldFocused  = Color(0xFFEFEFEF),
    borderFocused = Color(0xFFBDBDBD),
    borderIdle    = Color(0xFFDDDDDD),
    textStrong    = Color(0xFF000000),
    textPrimary   = Color(0xFF1C1C1C),
    textMuted     = Color(0xFF8A8A8A),
    textFaint     = Color(0xFFB0B0B0),
    textDisabled  = Color(0xFFC5C5C5),
    danger        = Color(0xFFB3261E),
    dividerLine   = Color(0xFFC4C4C4),
    checkerA      = Color(0xFFD6D6D6),
    checkerB      = Color(0xFFAAAAAA),
)

val LocalQuedallePalette = staticCompositionLocalOf { DarkPalette }

/** Stored tile colors with special meanings and the preset swatches. */
object QuedalleColors {
    /** Sentinel: "same color as app cards" — resolved per theme at render time. */
    const val TileAppColor = 0xFF141414.toInt()
    /** Sentinel: default divider line — resolved per theme at render time. */
    const val DividerDefault = 0xFF2A2A2A.toInt()
    /** Fully transparent spacer. */
    const val TileTransparent = 0x00000000

    /** 12 preset tile colors (3 rows × 4 columns in the picker). */
    val TilePresets: List<Int> = listOf(
        0xFF1C2B3A.toInt(), 0xFF2B1C3A.toInt(), 0xFF1C3A2B.toInt(), 0xFF3A2B1C.toInt(),
        0xFF3A1C1C.toInt(), 0xFF1C3A3A.toInt(), 0xFF2B3A1C.toInt(), 0xFF3A1C2B.toInt(),
        0xFF0F4C81.toInt(), 0xFF6B1F1F.toInt(), 0xFF1A5C40.toInt(), 0xFF7A5A10.toInt(),
    )
}

/** Resolves a stored tile color, mapping theme-dependent sentinels. */
@Composable
fun resolveTileColor(stored: Int): Color = when (stored) {
    QuedalleColors.TileAppColor   -> LocalQuedallePalette.current.card
    QuedalleColors.DividerDefault -> LocalQuedallePalette.current.dividerLine
    else -> Color(stored)
}

@Composable
fun LauncherTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalQuedallePalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
