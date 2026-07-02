package dev.mlg.quedalle

import dev.mlg.quedalle.data.DEFAULT_TILE_COLOR
import dev.mlg.quedalle.data.LayoutBackup
import dev.mlg.quedalle.data.TYPE_APP
import dev.mlg.quedalle.data.TYPE_DIVIDER
import dev.mlg.quedalle.data.TYPE_SPACER
import dev.mlg.quedalle.data.TileCodec
import dev.mlg.quedalle.data.TileDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileCodecTest {

    @Test
    fun `round trip preserves all fields`() {
        val defs = listOf(
            TileDef(TYPE_APP, "com.example.app", pkg = "com.example.app"),
            TileDef(TYPE_APP, "com.work.app#10", pkg = "com.work.app", userSerial = 10L, label = "Renamed"),
            TileDef(TYPE_SPACER, "sp_1", color = 0xFF123456.toInt()),
            TileDef(TYPE_DIVIDER, "dv_1", color = 0xFF2A2A2A.toInt()),
        )
        assertEquals(defs, TileCodec.decode(TileCodec.encode(defs)))
    }

    @Test
    fun `parses legacy v1_4 format`() {
        // Format written by the org.json implementation of versions <= 1.4
        val legacy = """[{"t":"app","id":"com.example","pkg":"com.example"},""" +
            """{"t":"spacer","id":"sp_x","c":-14540254},{"t":"divider","id":"dv_x","c":-13882324}]"""
        val defs = TileCodec.decode(legacy)!!
        assertEquals(3, defs.size)
        assertEquals(TileDef(TYPE_APP, "com.example", pkg = "com.example"), defs[0])
        assertEquals(-14540254, defs[1].color)
        assertEquals(TYPE_DIVIDER, defs[2].type)
    }

    @Test
    fun `corrupt entries are skipped not fatal`() {
        val raw = """[{"t":"app","id":"com.ok","pkg":"com.ok"},{"garbage":true},""" +
            """{"t":"app","id":""},{"t":"unknown","id":"x"},{"t":"spacer","id":"sp_1"}]"""
        val defs = TileCodec.decode(raw)!!
        assertEquals(listOf("com.ok", "sp_1"), defs.map { it.id })
        assertEquals(DEFAULT_TILE_COLOR, defs[1].color)
    }

    @Test
    fun `app tile without package is dropped`() {
        val defs = TileCodec.decode("""[{"t":"app","id":"com.x"}]""")!!
        assertTrue(defs.isEmpty())
    }

    @Test
    fun `unparseable input returns null`() {
        assertNull(TileCodec.decode(null))
        assertNull(TileCodec.decode("not json at all"))
        assertNull(TileCodec.decode("""{"an":"object"}"""))
    }

    @Test
    fun `unknown keys are ignored`() {
        val defs = TileCodec.decode("""[{"t":"app","id":"a","pkg":"a","future_field":42}]""")!!
        assertEquals(1, defs.size)
    }

    @Test
    fun `backup round trip`() {
        val backup = LayoutBackup(
            columns = 4, rows = 6, swipeDownNotifications = false,
            hidden = listOf("com.hidden"),
            tiles = listOf(TileDef(TYPE_APP, "com.a", pkg = "com.a")),
        )
        assertEquals(backup, TileCodec.decodeBackup(TileCodec.encodeBackup(backup)))
    }

    @Test
    fun `backup with corrupt tile keeps the rest`() {
        val raw = """{"columns":3,"rows":4,"tiles":[{"t":"app","id":"com.a","pkg":"com.a"},{"bad":1}]}"""
        val backup = TileCodec.decodeBackup(raw)!!
        assertEquals(1, backup.tiles.size)
        assertEquals(3, backup.columns)
    }

    @Test
    fun `invalid backup returns null`() {
        assertNull(TileCodec.decodeBackup("nope"))
    }
}
