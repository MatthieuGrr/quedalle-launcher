package dev.mlg.quedalle

import androidx.compose.ui.graphics.toArgb
import dev.mlg.quedalle.ui.theme.DarkPalette
import dev.mlg.quedalle.ui.theme.LightPalette
import dev.mlg.quedalle.ui.theme.withBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {

    private fun rgb(argb: Int) = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    @Test
    fun `surfaces follow a warm background instead of staying grey`() {
        val paper = LightPalette.withBackground(0xFFF2EFE9.toInt())
        val (r, _, b) = rgb(paper.surfaceDim.toArgb())
        assertTrue("search field should stay warm", r > b)
        assertEquals(0xFFF2EFE9.toInt(), paper.background.toArgb())
    }

    @Test
    fun `surfaces stay between the background and the text`() {
        val p = DarkPalette.withBackground(0xFF121212.toInt())
        val bg = rgb(0xFF121212.toInt()).first
        val field = rgb(p.surfaceDim.toArgb()).first
        val border = rgb(p.borderIdle.toArgb()).first
        assertTrue(bg < field && field < border && border < 0xFF)
    }

    @Test
    fun `text colors are left alone`() {
        val p = LightPalette.withBackground(0xFFF2EFE9.toInt())
        assertEquals(LightPalette.textStrong, p.textStrong)
        assertEquals(LightPalette.card, p.card)
    }
}
