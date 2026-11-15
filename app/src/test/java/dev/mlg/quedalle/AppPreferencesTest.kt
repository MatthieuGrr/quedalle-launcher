package dev.mlg.quedalle

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.mlg.quedalle.data.AppPreferences
import dev.mlg.quedalle.data.TYPE_APP
import dev.mlg.quedalle.data.TYPE_DIVIDER
import dev.mlg.quedalle.data.TYPE_SPACER
import dev.mlg.quedalle.data.TileDef
import dev.mlg.quedalle.model.TileText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy {
        PreferenceDataStoreFactory.create { tmp.newFile("test.preferences_pb").also { it.delete() } }
    }
    private val prefs by lazy { AppPreferences(store) }

    private suspend fun ids() = prefs.tileDefinitions.first().map { it.id }

    private suspend fun pin(vararg packages: String) =
        packages.map { prefs.togglePin(it, it, null) }

    @Test
    fun `pinning is refused once the grid is full`() = runBlocking {
        prefs.setGridColumns(2)
        prefs.setGridRows(1)
        assertEquals(listOf(true, true, false), pin("a", "b", "c"))
        assertEquals(listOf("a", "b"), ids())
    }

    @Test
    fun `toggling a pinned app unpins it`() = runBlocking {
        pin("a", "b")
        assertTrue(prefs.togglePin("a", "a", null))
        assertEquals(listOf("b"), ids())
    }

    @Test
    fun `dividers do not count against the row capacity`() = runBlocking {
        prefs.setGridColumns(2)
        prefs.setGridRows(1)
        pin("a")
        assertTrue(prefs.addTile(TileDef(TYPE_DIVIDER, "dv_1", color = 0)))
        assertFalse(prefs.addTile(TileDef(TYPE_SPACER, "sp_1", color = 0)))
    }

    @Test
    fun `grid cannot shrink below what the tiles need`() = runBlocking {
        pin("a", "b", "c", "d", "e") // 3 columns by default: two rows
        assertFalse(prefs.setGridRows(1))
        assertTrue(prefs.setGridRows(2))
        assertFalse(prefs.setGridColumns(2)) // would need a third row
        assertEquals(3, prefs.gridColumns.first())
        assertEquals(2, prefs.gridRows.first())
    }

    @Test
    fun `reorder keeps the tiles the grid did not display`() = runBlocking {
        pin("a", "ghost", "b", "c")
        // "ghost" was not on screen (app unavailable): the grid only reports a, b, c.
        prefs.reorderTiles(listOf("c", "a", "b"))
        assertEquals(listOf("c", "ghost", "a", "b"), ids())
    }

    @Test
    fun `reorder keeps style and label of the moved tiles`() = runBlocking {
        pin("a", "b")
        prefs.updateTile("a") { it.copy(label = "Alpha", color = 0x112233, texture = "iris") }
        prefs.reorderTiles(listOf("b", "a"))
        val a = prefs.tileDefinitions.first().last()
        assertEquals(TileDef(TYPE_APP, "a", pkg = "a", label = "Alpha", color = 0x112233, texture = "iris"), a)
    }

    @Test
    fun `removeTiles drops only the given ids`() = runBlocking {
        pin("a", "b", "c")
        prefs.removeTiles(setOf("a", "c", "unknown"))
        assertEquals(listOf("b"), ids())
    }

    @Test
    fun `legacy comma separated pins are still read and upgraded on write`() = runBlocking {
        store.edit { it[stringPreferencesKey("ordered_pins")] = "com.a,com.b" }
        assertEquals(
            listOf(TileDef(TYPE_APP, "com.a", pkg = "com.a"), TileDef(TYPE_APP, "com.b", pkg = "com.b")),
            prefs.tileDefinitions.first(),
        )
        prefs.removeTile("com.a")
        assertEquals(listOf("com.b"), ids())
    }

    @Test
    fun `export then import restores the whole configuration`() = runBlocking {
        prefs.setGridColumns(4)
        pin("a", "b")
        prefs.setHidden("secret", true)
        prefs.setGlobalBackground(0x445566)
        val backup = prefs.exportBackup()

        prefs.removeTiles(setOf("a", "b"))
        prefs.setGridColumns(2)
        prefs.setHidden("secret", false)

        assertTrue(prefs.importBackup(backup))
        assertEquals(listOf("a", "b"), ids())
        assertEquals(4, prefs.gridColumns.first())
        assertEquals(setOf("secret"), prefs.hiddenApps.first())
        assertEquals(0x445566, prefs.globalStyle.first().background)
    }

    @Test
    fun `import grows the rows so every tile fits`() = runBlocking {
        val tiles = (1..5).joinToString(",") { """{"t":"app","id":"p$it","pkg":"p$it"}""" }
        assertTrue(prefs.importBackup("""{"columns":2,"rows":1,"tiles":[$tiles]}"""))
        assertEquals(3, prefs.gridRows.first())
    }

    @Test
    fun `import drops duplicated tiles instead of storing them`() = runBlocking {
        val tile = """{"t":"app","id":"p","pkg":"p"}"""
        assertTrue(prefs.importBackup("""{"columns":3,"rows":4,"tiles":[$tile,$tile]}"""))
        assertEquals(listOf("p"), ids())
    }

    @Test
    fun `import rejects garbage and leaves the layout alone`() = runBlocking {
        pin("a")
        assertFalse(prefs.importBackup("not json"))
        assertEquals(listOf("a"), ids())
    }

    @Test
    fun `tile text is clamped, stored and carried by backups`() = runBlocking {
        assertEquals(TileText(), prefs.tileText.first())
        prefs.setTileText(TileText(sizeSp = 99, alignStart = true, bold = true))
        val stored = TileText(sizeSp = TileText.MAX_SIZE_SP, alignStart = true, bold = true)
        assertEquals(stored, prefs.tileText.first())

        val backup = prefs.exportBackup()
        prefs.setTileText(TileText())
        assertTrue(prefs.importBackup(backup))
        assertEquals(stored, prefs.tileText.first())
    }

    @Test
    fun `backup from an older version resets tile text to defaults`() = runBlocking {
        prefs.setTileText(TileText(sizeSp = 16, bold = true))
        assertTrue(prefs.importBackup("""{"columns":3,"rows":4,"tiles":[]}"""))
        assertEquals(TileText(), prefs.tileText.first())
    }
}
