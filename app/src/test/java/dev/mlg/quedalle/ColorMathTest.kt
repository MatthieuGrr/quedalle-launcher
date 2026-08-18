package dev.mlg.quedalle

import dev.mlg.quedalle.model.alphaOf
import dev.mlg.quedalle.model.argbToHex
import dev.mlg.quedalle.model.argbToHsl
import dev.mlg.quedalle.model.blendArgb
import dev.mlg.quedalle.model.hexToArgb
import dev.mlg.quedalle.model.hslToArgb
import dev.mlg.quedalle.model.prefersDarkText
import dev.mlg.quedalle.model.relativeLuminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    @Test
    fun `luminance extremes`() {
        assertEquals(0.0, relativeLuminance(0xFF000000.toInt()), 1e-6)
        assertEquals(1.0, relativeLuminance(0xFFFFFFFF.toInt()), 1e-6)
    }

    @Test
    fun `dark backgrounds get light text, light backgrounds get dark text`() {
        assertFalse(prefersDarkText(0xFF141414.toInt())) // dark card
        assertFalse(prefersDarkText(0xFF0F4C81.toInt())) // mid blue preset
        assertTrue(prefersDarkText(0xFFEBEBEB.toInt()))  // light card
        assertTrue(prefersDarkText(0xFFFFD54F.toInt()))  // yellow
    }

    @Test
    fun `alpha extraction`() {
        assertEquals(0xFF, alphaOf(0xFF123456.toInt()))
        assertEquals(0, alphaOf(0x00123456))
    }

    @Test
    fun `blend endpoints and midpoint`() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertEquals(black, blendArgb(black, white, 0f))
        assertEquals(white, blendArgb(black, white, 1f))
        val mid = blendArgb(black, white, 0.5f)
        assertEquals(0x7F, (mid shr 16) and 0xFF)
    }

    @Test
    fun `hsl round trip on primary colors`() {
        for (argb in listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF804020.toInt())) {
            val (h, s, l) = argbToHsl(argb)
            assertEquals(argb, hslToArgb(h, s, l))
        }
    }

    @Test
    fun `hsl handles greys without hue`() {
        val (h, s, _) = argbToHsl(0xFF808080.toInt())
        assertEquals(0f, h, 0f)
        assertEquals(0f, s, 0f)
        assertEquals(0xFF808080.toInt(), hslToArgb(0f, 0f, 0x80 / 255f))
    }

    @Test
    fun `hsl wraps hue`() {
        assertEquals(hslToArgb(0f, 1f, 0.5f), hslToArgb(360f, 1f, 0.5f))
    }

    @Test
    fun `hex formatting drops alpha and pads`() {
        assertEquals("FF00AA", argbToHex(0xFFFF00AA.toInt()))
        assertEquals("000000", argbToHex(0xFF000000.toInt()))
        assertEquals("0F4C81", argbToHex(0xFF0F4C81.toInt()))
        assertEquals("00000A", argbToHex(0xFF00000A.toInt()))
    }

    @Test
    fun `hex parsing accepts the usual forms`() {
        val expected = 0xFFFF00AA.toInt()
        assertEquals(expected, hexToArgb("FF00AA"))
        assertEquals(expected, hexToArgb("#FF00AA"))
        assertEquals(expected, hexToArgb("ff00aa"))
        assertEquals(expected, hexToArgb("  #FF00AA  "))
        assertEquals(expected, hexToArgb("F0A"))          // shorthand
        assertEquals(expected, hexToArgb("FFFF00AA"))     // explicit alpha
    }

    @Test
    fun `hex parsing rejects invalid input`() {
        assertNull(hexToArgb(""))
        assertNull(hexToArgb("#"))
        assertNull(hexToArgb("GG0000"))
        assertNull(hexToArgb("FF00"))      // wrong length
        assertNull(hexToArgb("FF00AAB"))   // wrong length
        assertNull(hexToArgb("nope"))
    }

    @Test
    fun `hex round trip`() {
        for (argb in listOf(0xFF141414.toInt(), 0xFF0F4C81.toInt(), 0xFFFFFFFF.toInt(), 0xFF7A5A10.toInt())) {
            assertEquals(argb, hexToArgb(argbToHex(argb)))
        }
    }
}
