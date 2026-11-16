package dev.mlg.quedalle

import dev.mlg.quedalle.data.LayoutBackup
import dev.mlg.quedalle.data.TYPE_APP
import dev.mlg.quedalle.data.TYPE_DIVIDER
import dev.mlg.quedalle.data.TYPE_SPACER
import dev.mlg.quedalle.data.TileCodec
import dev.mlg.quedalle.data.TileDef
import dev.mlg.quedalle.data.reordered
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
            TileDef(TYPE_APP, "com.styled", pkg = "com.styled",
                color = 0xFF0F4C81.toInt(), textColor = 0xFFFFD54F.toInt(), texture = "iris"),
            TileDef(TYPE_SPACER, "sp_1", color = 0xFF123456.toInt(), texture = "glass"),
            TileDef(TYPE_DIVIDER, "dv_1", color = 0xFF2A2A2A.toInt()),
        )
        assertEquals(defs, TileCodec.decode(TileCodec.encode(defs)))
    }

    @Test
    fun `v2_0 data without style fields gets defaults`() {
        val v20 = """[{"t":"app","id":"com.a","pkg":"com.a","c":-15461356}]"""
        val def = TileCodec.decode(v20)!!.single()
        assertEquals(null, def.textColor)
        assertEquals(null, def.texture)
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
        assertEquals(null, defs[1].color)
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
            theme = "light",
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

    @Test
    fun `duplicated ids keep only the first tile`() {
        val raw = """[{"t":"app","id":"com.a","pkg":"com.a","l":"first"},""" +
            """{"t":"spacer","id":"sp_1"},{"t":"app","id":"com.a","pkg":"com.a","l":"second"}]"""
        val defs = TileCodec.decode(raw)!!
        assertEquals(listOf("com.a", "sp_1"), defs.map { it.id })
        assertEquals("first", defs.first().label)
    }

    @Test
    fun `reordered moves the named tiles and leaves the others in place`() {
        val defs = listOf("a", "x", "b", "y", "c").map { TileDef(TYPE_APP, it, pkg = it) }
        assertEquals(listOf("c", "x", "a", "y", "b"), defs.reordered(listOf("c", "a", "b")).map { it.id })
    }

    @Test
    fun `reordered ignores ids that no longer exist`() {
        val defs = listOf("a", "b").map { TileDef(TYPE_APP, it, pkg = it) }
        assertEquals(listOf("b", "a"), defs.reordered(listOf("gone", "b", "a")).map { it.id })
    }

    @Test
    fun `shortcut, link and info tiles round trip`() {
        val defs = listOf(
            TileDef("shortcut", "sc_1", pkg = "com.chat", userSerial = 10L, label = "Alice", shortcutId = "conv-42"),
            TileDef("link", "ln_1", label = "News", uri = "https://example.org"),
            TileDef("info", "in_1", kind = "battery", color = 0xFF112233.toInt()),
        )
        assertEquals(defs, TileCodec.decode(TileCodec.encode(defs)))
    }

    @Test
    fun `incomplete shortcut, link and info tiles are skipped`() {
        val raw = """[{"t":"shortcut","id":"sc_1","pkg":"com.chat"},""" +
            """{"t":"link","id":"ln_1"},{"t":"info","id":"in_1","k":"weather"},""" +
            """{"t":"info","id":"in_2","k":"clock"}]"""
        assertEquals(listOf("in_2"), TileCodec.decode(raw)!!.map { it.id })
    }

    @Test
    fun `layouts decode leniently`() {
        val raw = """[{"id":"main","tiles":[{"t":"app","id":"a","pkg":"a"},{"t":"bogus","id":"x"}]},""" +
            """{"name":"no id"},{"id":"main","name":"duplicate"},{"id":"pf_1","name":"B","columns":4,"rows":6}]"""
        val profiles = TileCodec.decodeProfiles(raw)!!
        assertEquals(listOf("main", "pf_1"), profiles.map { it.id })
        assertEquals(listOf("a"), profiles[0].tiles.map { it.id })
        assertEquals(4 to 6, profiles[1].columns to profiles[1].rows)
        assertNull(TileCodec.decodeProfiles("not json"))
    }
}
