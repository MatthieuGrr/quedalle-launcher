package dev.mlg.quedalle.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.mlg.quedalle.model.Gesture
import dev.mlg.quedalle.model.GestureAction
import dev.mlg.quedalle.model.LookPreset
import dev.mlg.quedalle.model.ScreenLook
import dev.mlg.quedalle.model.TileShape
import dev.mlg.quedalle.model.ThemeMode
import dev.mlg.quedalle.model.TileStyle
import dev.mlg.quedalle.model.TileText
import dev.mlg.quedalle.model.requiredTileRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

/** Name and identity of a layout, without its tiles. */
data class ProfileName(val id: String, val name: String)

data class ProfileState(
    val activeId: String = AppPreferences.MAIN_PROFILE_ID,
    val profiles: List<ProfileName> = listOf(ProfileName(AppPreferences.MAIN_PROFILE_ID, "")),
)

/**
 * All persisted settings.
 *
 * Layouts ("profiles"): the tiles/columns/rows keys always hold the layout
 * on screen. The other layouts wait in [KEY_PROFILES]; the entry of the
 * active one there is only a placeholder, refreshed when switching away.
 */
class AppPreferences(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    companion object {
        private val KEY_ORDERED_PINS = stringPreferencesKey("ordered_pins") // legacy — read only
        private val KEY_TILES        = stringPreferencesKey("tiles")
        private val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        private val KEY_GRID_ROWS    = intPreferencesKey("grid_rows")
        private val KEY_HIDDEN_APPS  = stringSetPreferencesKey("hidden_apps")
        private val KEY_SWIPE_DOWN   = booleanPreferencesKey("swipe_down_notifications") // legacy — read only
        private val KEY_THEME        = stringPreferencesKey("theme")
        private val KEY_GLOBAL_BG    = intPreferencesKey("global_bg")
        private val KEY_GLOBAL_FC    = intPreferencesKey("global_fc")
        private val KEY_GLOBAL_TX    = stringPreferencesKey("global_tx")
        private val KEY_TEXT_SIZE    = intPreferencesKey("text_size")
        private val KEY_TEXT_ALIGN   = stringPreferencesKey("text_align")
        private val KEY_TEXT_BOLD    = booleanPreferencesKey("text_bold")
        private val KEY_TEXT_FONT    = stringPreferencesKey("text_font")
        private val KEY_TEXT_CASE    = stringPreferencesKey("text_case")
        private val KEY_TEXT_VERTICAL = stringPreferencesKey("text_vertical")
        private val KEY_SHAPE_CORNER = intPreferencesKey("shape_corner")
        private val KEY_SHAPE_GAP    = intPreferencesKey("shape_gap")
        private val KEY_SHAPE_MARGIN = intPreferencesKey("shape_margin")
        private val KEY_BG_DARK      = intPreferencesKey("screen_bg_dark")
        private val KEY_BG_LIGHT     = intPreferencesKey("screen_bg_light")
        private val KEY_HAPTICS      = booleanPreferencesKey("haptics")
        private val KEY_PROFILES     = stringPreferencesKey("profiles")
        private val KEY_ACTIVE_PROFILE = stringPreferencesKey("active_profile")

        private fun gestureKey(gesture: Gesture) = stringPreferencesKey("gesture_${gesture.key}")

        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 5
        const val MIN_ROWS = 1
        const val MAX_ROWS = 20
        const val DEFAULT_COLUMNS = 3
        const val DEFAULT_ROWS = 4
        const val MAX_PROFILES = 8
        const val MAIN_PROFILE_ID = "main"
    }

    val tileDefinitions: Flow<List<TileDef>> = store.data.map { defsOf(it) }

    val gridColumns: Flow<Int> = store.data.map { it[KEY_GRID_COLUMNS] ?: DEFAULT_COLUMNS }
    val gridRows: Flow<Int>    = store.data.map { it[KEY_GRID_ROWS]    ?: DEFAULT_ROWS }

    val hiddenApps: Flow<Set<String>> = store.data.map { it[KEY_HIDDEN_APPS] ?: emptySet() }

    val gestures: Flow<Map<Gesture, GestureAction>> = store.data.map { gesturesOf(it) }

    val themeMode: Flow<ThemeMode> = store.data.map { ThemeMode.fromKey(it[KEY_THEME]) }

    /** Default style of app tiles; null fields = theme background / auto text / flat. */
    val globalStyle: Flow<TileStyle> = store.data.map {
        TileStyle(it[KEY_GLOBAL_BG], it[KEY_GLOBAL_FC], it[KEY_GLOBAL_TX])
    }

    val tileText: Flow<TileText> = store.data.map { tileTextOf(it) }

    val tileShape: Flow<TileShape> = store.data.map {
        TileShape.of(it[KEY_SHAPE_CORNER], it[KEY_SHAPE_GAP], it[KEY_SHAPE_MARGIN])
    }

    val screenLook: Flow<ScreenLook> = store.data.map {
        ScreenLook(it[KEY_BG_DARK], it[KEY_BG_LIGHT], it[KEY_HAPTICS] ?: false)
    }

    val profiles: Flow<ProfileState> = store.data.map { prefs ->
        ProfileState(activeIdOf(prefs), profilesOf(prefs).map { ProfileName(it.id, it.name) })
    }

    /** Tiles of every layout, active or not. */
    suspend fun allTileDefinitions(): List<TileDef> = profilesOf(store.data.first()).flatMap { it.tiles }

    // ── Tiles of the active layout ───────────────────────────────────────────

    /**
     * Pins or unpins an app. Pinning is refused when the grid is full.
     * @return false when the tile did not fit.
     */
    suspend fun togglePin(key: String, packageName: String, userSerial: Long?): Boolean {
        var fitted = true
        store.edit { prefs ->
            val current = defsOf(prefs)
            if (current.any { it.type == TYPE_APP && it.id == key }) {
                prefs[KEY_TILES] = TileCodec.encode(current.filterNot { it.type == TYPE_APP && it.id == key })
            } else {
                val next = current + TileDef(TYPE_APP, key, pkg = packageName, userSerial = userSerial)
                if (fits(prefs, next)) prefs[KEY_TILES] = TileCodec.encode(next) else fitted = false
            }
        }
        return fitted
    }

    /** Reorders the displayed tiles; tiles absent from [orderedIds] keep their slot. */
    suspend fun reorderTiles(orderedIds: List<String>) {
        store.edit { prefs ->
            prefs[KEY_TILES] = TileCodec.encode(defsOf(prefs).reordered(orderedIds))
        }
    }

    /** @return false when the grid is full. */
    suspend fun addTile(def: TileDef): Boolean {
        var fitted = true
        store.edit { prefs ->
            val next = defsOf(prefs) + def
            if (fits(prefs, next)) prefs[KEY_TILES] = TileCodec.encode(next) else fitted = false
        }
        return fitted
    }

    suspend fun removeTile(id: String) {
        store.edit { prefs ->
            prefs[KEY_TILES] = TileCodec.encode(defsOf(prefs).filter { it.id != id })
        }
    }

    suspend fun updateTile(id: String, transform: (TileDef) -> TileDef) {
        store.edit { prefs ->
            prefs[KEY_TILES] = TileCodec.encode(defsOf(prefs).map { if (it.id == id) transform(it) else it })
        }
    }

    /** @return false when existing tiles would no longer fit. */
    suspend fun setGridColumns(columns: Int): Boolean {
        val target = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        var fitted = true
        store.edit { prefs ->
            val rows = prefs[KEY_GRID_ROWS] ?: DEFAULT_ROWS
            if (requiredTileRows(defsOf(prefs).fullRowFlags(), target) <= rows) {
                prefs[KEY_GRID_COLUMNS] = target
            } else {
                fitted = false
            }
        }
        return fitted
    }

    /** @return false when existing tiles would no longer fit. */
    suspend fun setGridRows(rows: Int): Boolean {
        val target = rows.coerceIn(MIN_ROWS, MAX_ROWS)
        var fitted = true
        store.edit { prefs ->
            val columns = prefs[KEY_GRID_COLUMNS] ?: DEFAULT_COLUMNS
            if (requiredTileRows(defsOf(prefs).fullRowFlags(), columns) <= target) {
                prefs[KEY_GRID_ROWS] = target
            } else {
                fitted = false
            }
        }
        return fitted
    }

    // ── Tiles of every layout ────────────────────────────────────────────────

    /** Removes the tiles matching [predicate] from every layout (uninstalled apps). */
    suspend fun removeTilesEverywhere(predicate: (TileDef) -> Boolean) {
        store.edit { prefs -> prefs.mapEveryLayout { tiles -> tiles.filterNot(predicate) } }
    }

    /** Removes every per-tile style override so all text tiles follow the global style. */
    suspend fun applyGlobalStyleToAllTiles() {
        store.edit { prefs ->
            prefs.mapEveryLayout { tiles ->
                tiles.map {
                    if (it.type == TYPE_SPACER || it.type == TYPE_DIVIDER) it
                    else it.copy(color = null, textColor = null, texture = null)
                }
            }
        }
    }

    // ── Layouts ──────────────────────────────────────────────────────────────

    /** Creates an empty layout and shows it. @return false when [MAX_PROFILES] is reached. */
    suspend fun createProfile(name: String): Boolean {
        var created = true
        store.edit { prefs ->
            val all = profilesOf(prefs)
            if (all.size >= MAX_PROFILES) {
                created = false
                return@edit
            }
            val profile = GridProfile(
                id = "pf_${UUID.randomUUID()}",
                name = name.trim(),
                columns = prefs[KEY_GRID_COLUMNS] ?: DEFAULT_COLUMNS,
                rows = prefs[KEY_GRID_ROWS] ?: DEFAULT_ROWS,
            )
            prefs[KEY_PROFILES] = TileCodec.encodeProfiles(all + profile)
            prefs.show(profile)
        }
        return created
    }

    suspend fun switchProfile(id: String) {
        store.edit { prefs ->
            val all = profilesOf(prefs)
            val target = all.firstOrNull { it.id == id } ?: return@edit
            if (target.id == activeIdOf(prefs)) return@edit
            prefs[KEY_PROFILES] = TileCodec.encodeProfiles(all)
            prefs.show(target)
        }
    }

    /** Shows the layout after the active one, wrapping around. */
    suspend fun nextProfile() {
        store.edit { prefs ->
            val all = profilesOf(prefs)
            if (all.size < 2) return@edit
            val active = all.indexOfFirst { it.id == activeIdOf(prefs) }
            prefs[KEY_PROFILES] = TileCodec.encodeProfiles(all)
            prefs.show(all[(active + 1) % all.size])
        }
    }

    suspend fun renameProfile(id: String, name: String) {
        store.edit { prefs ->
            val active = activeIdOf(prefs)
            prefs[KEY_PROFILES] = TileCodec.encodeProfiles(
                profilesOf(prefs).map { if (it.id == id) it.copy(name = name.trim()) else it }
            )
            prefs[KEY_ACTIVE_PROFILE] = active
        }
    }

    /** Deletes a layout and its tiles. @return false for the last remaining layout. */
    suspend fun deleteProfile(id: String): Boolean {
        var deleted = true
        store.edit { prefs ->
            val all = profilesOf(prefs)
            val remaining = all.filter { it.id != id }
            if (remaining.isEmpty() || remaining.size == all.size) {
                deleted = false
                return@edit
            }
            val active = activeIdOf(prefs)
            prefs[KEY_PROFILES] = TileCodec.encodeProfiles(remaining)
            if (id == active) prefs.show(remaining.first()) else prefs[KEY_ACTIVE_PROFILE] = active
        }
        return deleted
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    suspend fun setHidden(key: String, hidden: Boolean) {
        store.edit { prefs ->
            val current = prefs[KEY_HIDDEN_APPS] ?: emptySet()
            prefs[KEY_HIDDEN_APPS] = if (hidden) current + key else current - key
        }
    }

    suspend fun setGesture(gesture: Gesture, action: GestureAction) {
        store.edit { it[gestureKey(gesture)] = action.encode() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.key }
    }

    suspend fun setGlobalBackground(color: Int) {
        store.edit { it[KEY_GLOBAL_BG] = color }
    }

    suspend fun setGlobalTextColor(color: Int?) {
        store.edit { prefs ->
            if (color != null) prefs[KEY_GLOBAL_FC] = color else prefs.remove(KEY_GLOBAL_FC)
        }
    }

    suspend fun setGlobalTexture(texture: String?) {
        store.edit { prefs ->
            if (texture != null) prefs[KEY_GLOBAL_TX] = texture else prefs.remove(KEY_GLOBAL_TX)
        }
    }

    suspend fun setTileText(text: TileText) {
        store.edit { it.put(text) }
    }

    suspend fun setTileShape(shape: TileShape) {
        store.edit { it.put(shape) }
    }

    /** Background of the dark or light theme; null follows the theme. */
    suspend fun setScreenBackground(dark: Boolean, color: Int?) {
        store.edit { it.putOrRemove(if (dark) KEY_BG_DARK else KEY_BG_LIGHT, color) }
    }

    suspend fun setHaptics(enabled: Boolean) {
        store.edit { it[KEY_HAPTICS] = enabled }
    }

    /**
     * Applies a ready-made look in one go. Per-tile overrides, the text
     * size and the other theme's background are left alone.
     */
    suspend fun applyPreset(preset: LookPreset) {
        store.edit { prefs ->
            prefs[KEY_THEME] = preset.theme.key
            prefs.putOrRemove(if (preset.theme == ThemeMode.LIGHT) KEY_BG_LIGHT else KEY_BG_DARK, preset.background)
            prefs.putOrRemove(KEY_GLOBAL_BG, preset.style.background)
            prefs.putOrRemove(KEY_GLOBAL_FC, preset.style.textColor)
            prefs.putOrRemove(KEY_GLOBAL_TX, preset.style.texture)
            prefs.put(preset.shape)
            prefs.put(preset.text.copy(sizeSp = tileTextOf(prefs).sizeSp))
        }
    }

    // ── Backup ───────────────────────────────────────────────────────────────

    suspend fun exportBackup(): String {
        val prefs = store.data.first()
        val gestures = gesturesOf(prefs)
        val hasProfiles = prefs[KEY_PROFILES] != null
        return TileCodec.encodeBackup(
            LayoutBackup(
                columns = prefs[KEY_GRID_COLUMNS] ?: DEFAULT_COLUMNS,
                rows = prefs[KEY_GRID_ROWS] ?: DEFAULT_ROWS,
                swipeDownNotifications = gestures[Gesture.SWIPE_DOWN] == GestureAction.Notifications,
                theme = prefs[KEY_THEME] ?: ThemeMode.SYSTEM.key,
                globalBackground = prefs[KEY_GLOBAL_BG],
                globalTextColor = prefs[KEY_GLOBAL_FC],
                globalTexture = prefs[KEY_GLOBAL_TX],
                tileTextSize = prefs[KEY_TEXT_SIZE],
                tileTextAlign = prefs[KEY_TEXT_ALIGN],
                tileTextBold = prefs[KEY_TEXT_BOLD],
                tileTextFont = prefs[KEY_TEXT_FONT],
                tileTextCase = prefs[KEY_TEXT_CASE],
                tileTextVertical = prefs[KEY_TEXT_VERTICAL],
                cornerRadius = prefs[KEY_SHAPE_CORNER],
                tileGap = prefs[KEY_SHAPE_GAP],
                gridMargin = prefs[KEY_SHAPE_MARGIN],
                backgroundDark = prefs[KEY_BG_DARK],
                backgroundLight = prefs[KEY_BG_LIGHT],
                haptics = prefs[KEY_HAPTICS],
                hidden = (prefs[KEY_HIDDEN_APPS] ?: emptySet()).toList(),
                tiles = defsOf(prefs),
                gestures = gestures.entries.associate { (gesture, action) -> gesture.key to action.encode() },
                profiles = if (hasProfiles) profilesOf(prefs) else null,
                activeProfile = if (hasProfiles) activeIdOf(prefs) else null,
            )
        )
    }

    /** Replaces the whole configuration. @return false when [raw] is not a valid backup. */
    suspend fun importBackup(raw: String): Boolean {
        val backup = TileCodec.decodeBackup(raw) ?: return false

        // Every layout must fit a legal grid; rows grow as needed.
        fun fitted(profile: GridProfile): GridProfile? {
            val columns = profile.columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            val needed = requiredTileRows(profile.tiles.fullRowFlags(), columns)
            if (needed > MAX_ROWS) return null
            return profile.copy(columns = columns, rows = maxOf(profile.rows.coerceIn(MIN_ROWS, MAX_ROWS), needed))
        }

        val profiles = backup.profiles.orEmpty().take(MAX_PROFILES).map { fitted(it) ?: return false }
        val active = profiles.firstOrNull { it.id == backup.activeProfile } ?: profiles.firstOrNull()
        val onScreen = active
            ?: fitted(GridProfile(MAIN_PROFILE_ID, "", backup.columns, backup.rows, backup.tiles))
            ?: return false

        store.edit { prefs ->
            if (active != null) prefs[KEY_PROFILES] = TileCodec.encodeProfiles(profiles) else prefs.remove(KEY_PROFILES)
            prefs.show(onScreen)
            if (active == null) prefs.remove(KEY_ACTIVE_PROFILE)

            prefs[KEY_HIDDEN_APPS] = backup.hidden.toSet()
            prefs[KEY_THEME] = ThemeMode.fromKey(backup.theme).key
            prefs.putOrRemove(KEY_GLOBAL_BG, backup.globalBackground)
            prefs.putOrRemove(KEY_GLOBAL_FC, backup.globalTextColor)
            prefs.putOrRemove(KEY_GLOBAL_TX, backup.globalTexture)
            prefs.put(
                TileText.of(
                    backup.tileTextSize, backup.tileTextAlign, backup.tileTextBold,
                    backup.tileTextFont, backup.tileTextCase, backup.tileTextVertical,
                )
            )
            prefs.put(TileShape.of(backup.cornerRadius, backup.tileGap, backup.gridMargin))
            prefs.putOrRemove(KEY_BG_DARK, backup.backgroundDark)
            prefs.putOrRemove(KEY_BG_LIGHT, backup.backgroundLight)
            prefs[KEY_HAPTICS] = backup.haptics ?: false

            // Backups older than 3.0 only know the swipe-down switch.
            prefs[KEY_SWIPE_DOWN] = backup.swipeDownNotifications
            Gesture.entries.forEach { gesture ->
                val stored = backup.gestures?.get(gesture.key)
                if (stored != null) prefs[gestureKey(gesture)] = GestureAction.decode(stored).encode()
                else prefs.remove(gestureKey(gesture))
            }
        }
        return true
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun tileTextOf(prefs: Preferences) = TileText.of(
        prefs[KEY_TEXT_SIZE], prefs[KEY_TEXT_ALIGN], prefs[KEY_TEXT_BOLD],
        prefs[KEY_TEXT_FONT], prefs[KEY_TEXT_CASE], prefs[KEY_TEXT_VERTICAL],
    )

    private fun MutablePreferences.put(text: TileText) {
        this[KEY_TEXT_SIZE] = text.sizeSp.coerceIn(TileText.MIN_SIZE_SP, TileText.MAX_SIZE_SP)
        this[KEY_TEXT_ALIGN] = text.alignKey
        this[KEY_TEXT_BOLD] = text.bold
        this[KEY_TEXT_FONT] = text.font.key
        this[KEY_TEXT_CASE] = text.case.key
        this[KEY_TEXT_VERTICAL] = text.vertical.key
    }

    private fun MutablePreferences.put(shape: TileShape) {
        val valid = TileShape.of(shape.cornerDp, shape.gapDp, shape.marginDp)
        this[KEY_SHAPE_CORNER] = valid.cornerDp
        this[KEY_SHAPE_GAP] = valid.gapDp
        this[KEY_SHAPE_MARGIN] = valid.marginDp
    }

    private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value != null) this[key] = value else remove(key)
    }

    private fun fits(prefs: Preferences, defs: List<TileDef>): Boolean {
        val columns = prefs[KEY_GRID_COLUMNS] ?: DEFAULT_COLUMNS
        val rows = prefs[KEY_GRID_ROWS] ?: DEFAULT_ROWS
        return requiredTileRows(defs.fullRowFlags(), columns) <= rows
    }

    private fun defsOf(prefs: Preferences): List<TileDef> =
        TileCodec.decode(prefs[KEY_TILES])
            ?: prefs[KEY_ORDERED_PINS]
                ?.split(",")?.filter { it.isNotEmpty() }
                ?.map { TileDef(TYPE_APP, it, pkg = it) }
            ?: emptyList()

    private fun gesturesOf(prefs: Preferences): Map<Gesture, GestureAction> =
        Gesture.entries.associateWith { gesture ->
            prefs[gestureKey(gesture)]?.let(GestureAction::decode) ?: when (gesture) {
                // Until 2.3 swipe-down was a plain switch, on by default.
                Gesture.SWIPE_DOWN ->
                    if (prefs[KEY_SWIPE_DOWN] ?: true) GestureAction.Notifications else GestureAction.None
                else -> GestureAction.None
            }
        }

    private fun storedProfiles(prefs: Preferences): List<GridProfile> =
        TileCodec.decodeProfiles(prefs[KEY_PROFILES]).orEmpty()

    private fun activeIdOf(prefs: Preferences): String {
        val stored = storedProfiles(prefs)
        return prefs[KEY_ACTIVE_PROFILE]?.takeIf { id -> stored.any { it.id == id } }
            ?: stored.firstOrNull()?.id
            ?: MAIN_PROFILE_ID
    }

    /** Every layout with its real content: the active one is read from the live keys. */
    private fun profilesOf(prefs: Preferences): List<GridProfile> {
        fun live(id: String, name: String) = GridProfile(
            id, name,
            columns = prefs[KEY_GRID_COLUMNS] ?: DEFAULT_COLUMNS,
            rows = prefs[KEY_GRID_ROWS] ?: DEFAULT_ROWS,
            tiles = defsOf(prefs),
        )
        val stored = storedProfiles(prefs)
        if (stored.isEmpty()) return listOf(live(MAIN_PROFILE_ID, ""))
        val active = activeIdOf(prefs)
        return stored.map { if (it.id == active) live(it.id, it.name) else it }
    }

    /** Puts [profile] on screen. */
    private fun MutablePreferences.show(profile: GridProfile) {
        this[KEY_TILES] = TileCodec.encode(profile.tiles)
        this[KEY_GRID_COLUMNS] = profile.columns
        this[KEY_GRID_ROWS] = profile.rows
        this[KEY_ACTIVE_PROFILE] = profile.id
    }

    private fun MutablePreferences.mapEveryLayout(transform: (List<TileDef>) -> List<TileDef>) {
        this[KEY_TILES] = TileCodec.encode(transform(defsOf(this)))
        val stored = storedProfiles(this)
        if (stored.isNotEmpty()) {
            this[KEY_PROFILES] = TileCodec.encodeProfiles(stored.map { it.copy(tiles = transform(it.tiles)) })
        }
    }
}
