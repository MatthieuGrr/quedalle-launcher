package dev.mlg.quedalle

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.mlg.quedalle.data.AppPreferences
import dev.mlg.quedalle.data.TYPE_APP
import dev.mlg.quedalle.data.TYPE_DIVIDER
import dev.mlg.quedalle.data.TYPE_SPACER
import dev.mlg.quedalle.data.TileDef
import dev.mlg.quedalle.model.Gesture
import dev.mlg.quedalle.model.GestureAction
import dev.mlg.quedalle.model.LookPreset
import dev.mlg.quedalle.model.ScreenLook
import dev.mlg.quedalle.model.TextCase
import dev.mlg.quedalle.model.ThemeMode
import dev.mlg.quedalle.model.TileFont
import dev.mlg.quedalle.model.TileShape
import dev.mlg.quedalle.model.TileStyle
import dev.mlg.quedalle.model.TileText
import dev.mlg.quedalle.model.VerticalAnchor
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
    fun `removeTilesEverywhere drops only the matching tiles`() = runBlocking {
        pin("a", "b", "c")
        prefs.removeTilesEverywhere { it.id == "a" || it.id == "c" }
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

        prefs.removeTilesEverywhere { true }
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

    // ── Layouts ──────────────────────────────────────────────────────────────

    private suspend fun profileIds() = prefs.profiles.first().profiles.map { it.id }
    private suspend fun activeProfile() = prefs.profiles.first().activeId

    @Test
    fun `a fresh install has one unnamed layout`() = runBlocking {
        val state = prefs.profiles.first()
        assertEquals(listOf(AppPreferences.MAIN_PROFILE_ID), state.profiles.map { it.id })
        assertEquals(AppPreferences.MAIN_PROFILE_ID, state.activeId)
    }

    @Test
    fun `a new layout starts empty and each layout keeps its own tiles and grid`() = runBlocking {
        pin("a", "b")
        assertTrue(prefs.createProfile(" Weekend "))
        assertEquals(emptyList<String>(), ids())
        assertEquals("Weekend", prefs.profiles.first().profiles.last().name)

        prefs.setGridColumns(5)
        pin("c")
        val weekend = activeProfile()

        prefs.switchProfile(AppPreferences.MAIN_PROFILE_ID)
        assertEquals(listOf("a", "b"), ids())
        assertEquals(AppPreferences.DEFAULT_COLUMNS, prefs.gridColumns.first())

        prefs.switchProfile(weekend)
        assertEquals(listOf("c"), ids())
        assertEquals(5, prefs.gridColumns.first())
    }

    @Test
    fun `nextProfile cycles and does nothing with a single layout`() = runBlocking {
        prefs.nextProfile()
        assertEquals(AppPreferences.MAIN_PROFILE_ID, activeProfile())

        prefs.createProfile("B")
        val b = activeProfile()
        prefs.nextProfile()
        assertEquals(AppPreferences.MAIN_PROFILE_ID, activeProfile())
        prefs.nextProfile()
        assertEquals(b, activeProfile())
    }

    @Test
    fun `renaming a layout keeps the active one and its tiles`() = runBlocking {
        pin("a")
        prefs.renameProfile(AppPreferences.MAIN_PROFILE_ID, "Work")
        assertEquals("Work", prefs.profiles.first().profiles.single().name)
        assertEquals(AppPreferences.MAIN_PROFILE_ID, activeProfile())
        assertEquals(listOf("a"), ids())
    }

    @Test
    fun `deleting the active layout shows another one, the last one cannot go`() = runBlocking {
        pin("a")
        prefs.createProfile("B")
        pin("b")
        assertTrue(prefs.deleteProfile(activeProfile()))
        assertEquals(listOf(AppPreferences.MAIN_PROFILE_ID), profileIds())
        assertEquals(listOf("a"), ids())
        assertFalse(prefs.deleteProfile(AppPreferences.MAIN_PROFILE_ID))
        assertFalse(prefs.deleteProfile("unknown"))
    }

    @Test
    fun `deleting a background layout leaves the screen alone`() = runBlocking {
        pin("a")
        prefs.createProfile("B")
        pin("b")
        assertTrue(prefs.deleteProfile(AppPreferences.MAIN_PROFILE_ID))
        assertEquals(listOf("b"), ids())
        assertEquals(1, profileIds().size)
    }

    @Test
    fun `layouts are capped`() = runBlocking {
        repeat(AppPreferences.MAX_PROFILES - 1) { assertTrue(prefs.createProfile("p$it")) }
        assertFalse(prefs.createProfile("one too many"))
        assertEquals(AppPreferences.MAX_PROFILES, profileIds().size)
    }

    @Test
    fun `uninstalled apps are removed from background layouts too`() = runBlocking {
        pin("a", "gone")
        prefs.createProfile("B")
        pin("gone", "b")
        prefs.removeTilesEverywhere { it.id == "gone" }
        assertEquals(listOf("b"), ids())
        assertEquals(listOf("a", "b"), prefs.allTileDefinitions().map { it.id })
    }

    @Test
    fun `backup carries every layout and which one is shown`() = runBlocking {
        pin("a")
        prefs.createProfile("B")
        pin("b")
        val shown = activeProfile()
        val backup = prefs.exportBackup()

        prefs.deleteProfile(shown)
        prefs.removeTilesEverywhere { true }

        assertTrue(prefs.importBackup(backup))
        assertEquals(shown, activeProfile())
        assertEquals(listOf("b"), ids())
        prefs.switchProfile(AppPreferences.MAIN_PROFILE_ID)
        assertEquals(listOf("a"), ids())
    }

    @Test
    fun `importing a single-layout backup drops the extra layouts`() = runBlocking {
        prefs.createProfile("B")
        assertTrue(prefs.importBackup("""{"columns":3,"rows":4,"tiles":[{"t":"app","id":"p","pkg":"p"}]}"""))
        assertEquals(listOf(AppPreferences.MAIN_PROFILE_ID), profileIds())
        assertEquals(listOf("p"), ids())
    }

    // ── Gestures ─────────────────────────────────────────────────────────────

    @Test
    fun `swipe down opens notifications by default, other gestures do nothing`() = runBlocking {
        assertEquals(
            mapOf(
                Gesture.SWIPE_DOWN to GestureAction.Notifications,
                Gesture.SWIPE_UP to GestureAction.None,
                Gesture.DOUBLE_TAP to GestureAction.None,
            ),
            prefs.gestures.first(),
        )
    }

    @Test
    fun `the old swipe-down switch still decides until a gesture is chosen`() = runBlocking {
        store.edit { it[booleanPreferencesKey("swipe_down_notifications")] = false }
        assertEquals(GestureAction.None, prefs.gestures.first()[Gesture.SWIPE_DOWN])
        prefs.setGesture(Gesture.SWIPE_DOWN, GestureAction.QuickSettings)
        assertEquals(GestureAction.QuickSettings, prefs.gestures.first()[Gesture.SWIPE_DOWN])
    }

    @Test
    fun `gestures survive a backup, old backups fall back to the switch`() = runBlocking {
        prefs.setGesture(Gesture.SWIPE_UP, GestureAction.LaunchApp("com.a#10"))
        prefs.setGesture(Gesture.DOUBLE_TAP, GestureAction.Search)
        val backup = prefs.exportBackup()
        prefs.setGesture(Gesture.SWIPE_UP, GestureAction.None)

        assertTrue(prefs.importBackup(backup))
        assertEquals(GestureAction.LaunchApp("com.a#10"), prefs.gestures.first()[Gesture.SWIPE_UP])
        assertEquals(GestureAction.Search, prefs.gestures.first()[Gesture.DOUBLE_TAP])

        assertTrue(prefs.importBackup("""{"columns":3,"rows":4,"swipeDownNotifications":false}"""))
        assertEquals(Gesture.entries.associateWith { GestureAction.None }, prefs.gestures.first())
    }

    @Test
    fun `applying the global style clears overrides of every text tile in every layout`() = runBlocking {
        pin("a")
        prefs.updateTile("a") { it.copy(color = 1, textColor = 2, texture = "iris") }
        prefs.addTile(TileDef(TYPE_SPACER, "sp_1", color = 7))
        prefs.createProfile("B")
        prefs.addTile(TileDef("info", "in_1", kind = "clock", color = 3))

        prefs.applyGlobalStyleToAllTiles()
        val all = prefs.allTileDefinitions().associateBy { it.id }
        assertEquals(TileDef(TYPE_APP, "a", pkg = "a"), all["a"])
        assertEquals(null, all["in_1"]!!.color)
        assertEquals(7, all["sp_1"]!!.color)
    }

    // ── Look ─────────────────────────────────────────────────────────────────

    @Test
    fun `shape is clamped and stored`() = runBlocking {
        assertEquals(TileShape(), prefs.tileShape.first())
        prefs.setTileShape(TileShape(cornerDp = 99, gapDp = -3, marginDp = 0))
        assertEquals(TileShape(TileShape.MAX_CORNER, 0, 0), prefs.tileShape.first())
    }

    @Test
    fun `each theme keeps its own background`() = runBlocking {
        prefs.setScreenBackground(dark = true, color = 0x101010)
        prefs.setScreenBackground(dark = false, color = 0xF0F0F0)
        prefs.setScreenBackground(dark = true, color = null)
        assertEquals(ScreenLook(backgroundDark = null, backgroundLight = 0xF0F0F0), prefs.screenLook.first())
    }

    @Test
    fun `a look sets everything but keeps text size, overrides and the other theme`() = runBlocking {
        pin("a")
        prefs.updateTile("a") { it.copy(color = 0x123456) }
        prefs.setTileText(TileText(sizeSp = 18))
        prefs.setScreenBackground(dark = true, color = 0x101010)
        val paper = LookPreset.all.first { it.key == LookPreset.PAPER }

        prefs.applyPreset(paper)

        assertEquals(ThemeMode.LIGHT, prefs.themeMode.first())
        assertEquals(paper.style, prefs.globalStyle.first())
        assertEquals(paper.shape, prefs.tileShape.first())
        assertEquals(paper.text.copy(sizeSp = 18), prefs.tileText.first())
        assertEquals(ScreenLook(backgroundDark = 0x101010, backgroundLight = paper.background), prefs.screenLook.first())
        assertEquals(0x123456, prefs.tileDefinitions.first().single().color)
    }

    @Test
    fun `the first look restores the defaults`() = runBlocking {
        prefs.applyPreset(LookPreset.all.first { it.key == LookPreset.MOSAIC })
        prefs.applyPreset(LookPreset.all.first())
        assertEquals(TileStyle(), prefs.globalStyle.first())
        assertEquals(TileShape(), prefs.tileShape.first())
        assertEquals(TileText(), prefs.tileText.first())
    }

    @Test
    fun `the whole look survives a backup, an old backup resets it`() = runBlocking {
        val text = TileText(14, true, true, TileFont.MONO, TextCase.UPPER, VerticalAnchor.BOTTOM)
        val shape = TileShape(0, 2, 0)
        prefs.setTileText(text)
        prefs.setTileShape(shape)
        prefs.setScreenBackground(dark = false, color = 0xF2EFE9)
        prefs.setHaptics(true)
        val backup = prefs.exportBackup()

        assertTrue(prefs.importBackup("""{"columns":3,"rows":4}"""))
        assertEquals(TileText(), prefs.tileText.first())
        assertEquals(TileShape(), prefs.tileShape.first())
        assertEquals(ScreenLook(), prefs.screenLook.first())

        assertTrue(prefs.importBackup(backup))
        assertEquals(text, prefs.tileText.first())
        assertEquals(shape, prefs.tileShape.first())
        assertEquals(ScreenLook(backgroundLight = 0xF2EFE9, haptics = true), prefs.screenLook.first())
    }

    @Test
    fun `letter case only changes what is drawn`() {
        assertEquals("CAFÉ", TileText(case = TextCase.UPPER).display("Café"))
        assertEquals("café", TileText(case = TextCase.LOWER).display("Café"))
        assertEquals("Café", TileText().display("Café"))
    }
}
