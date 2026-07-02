package dev.mlg.quedalle

import dev.mlg.quedalle.model.TEXTURE_NONE
import dev.mlg.quedalle.model.TEXT_COLOR_AUTO
import dev.mlg.quedalle.model.TileStyle
import dev.mlg.quedalle.model.mergeTileStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class TileStyleTest {

    private val global = TileStyle(
        background = 0xFF0F4C81.toInt(),
        textColor = 0xFFFFD54F.toInt(),
        texture = "iris",
    )

    @Test
    fun `empty override inherits everything from global`() {
        assertEquals(global, mergeTileStyle(TileStyle(), global))
    }

    @Test
    fun `explicit values win over global`() {
        val override = TileStyle(0xFF111111.toInt(), 0xFF222222.toInt(), "glass")
        assertEquals(override, mergeTileStyle(override, global))
    }

    @Test
    fun `auto and none sentinels force defaults despite global`() {
        val merged = mergeTileStyle(TileStyle(textColor = TEXT_COLOR_AUTO, texture = TEXTURE_NONE), global)
        assertEquals(global.background, merged.background)
        assertEquals(null, merged.textColor) // auto
        assertEquals(null, merged.texture)   // flat
    }

    @Test
    fun `default global means theme, auto, flat`() {
        val merged = mergeTileStyle(TileStyle(), TileStyle())
        assertEquals(TileStyle(null, null, null), merged)
    }
}
