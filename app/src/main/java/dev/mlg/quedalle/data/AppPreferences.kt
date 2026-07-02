package dev.mlg.quedalle.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.mlg.quedalle.model.ThemeMode
import dev.mlg.quedalle.model.requiredTileRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_ORDERED_PINS = stringPreferencesKey("ordered_pins") // legacy — read only
        private val KEY_TILES        = stringPreferencesKey("tiles")
        private val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        private val KEY_GRID_ROWS    = intPreferencesKey("grid_rows")
        private val KEY_HIDDEN_APPS  = stringSetPreferencesKey("hidden_apps")
        private val KEY_SWIPE_DOWN   = booleanPreferencesKey("swipe_down_notifications")
        private val KEY_THEME        = stringPreferencesKey("theme")

        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 5
        const val MIN_ROWS = 1
        const val MAX_ROWS = 20
        const val DEFAULT_COLUMNS = 3
        const val DEFAULT_ROWS = 4
    }

    val tileDefinitions: Flow<List<TileDef>> = context.dataStore.data.map { defsOf(it) }

    val gridColumns: Flow<Int> = context.dataStore.data.map { it[KEY_GRID_COLUMNS] ?: DEFAULT_COLUMNS }
    val gridRows: Flow<Int>    = context.dataStore.data.map { it[KEY_GRID_ROWS]    ?: DEFAULT_ROWS }

    val hiddenApps: Flow<Set<String>> = context.dataStore.data.map { it[KEY_HIDDEN_APPS] ?: emptySet() }

    val swipeDownNotifications: Flow<Boolean> = context.dataStore.data.map { it[KEY_SWIPE_DOWN] ?: true }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.fromKey(it[KEY_THEME]) }

    /**
     * Pins or unpins an app. Pinning is refused when the grid is full.
     * @return false when the tile did not fit.
     */
    suspend fun togglePin(key: String, packageName: String, userSerial: Long?): Boolean {
        var fitted = true
        context.dataStore.edit { prefs ->
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

    suspend fun saveTiles(defs: List<TileDef>) {
        context.dataStore.edit { it[KEY_TILES] = TileCodec.encode(defs) }
    }

    /** @return false when the grid is full. */
    suspend fun addTile(def: TileDef): Boolean {
        var fitted = true
        context.dataStore.edit { prefs ->
            val next = defsOf(prefs) + def
            if (fits(prefs, next)) prefs[KEY_TILES] = TileCodec.encode(next) else fitted = false
        }
        return fitted
    }

    suspend fun removeTile(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TILES] = TileCodec.encode(defsOf(prefs).filter { it.id != id })
        }
    }

    suspend fun updateTile(id: String, transform: (TileDef) -> TileDef) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TILES] = TileCodec.encode(defsOf(prefs).map { if (it.id == id) transform(it) else it })
        }
    }

    /** @return false when existing tiles would no longer fit. */
    suspend fun setGridColumns(columns: Int): Boolean {
        val target = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        var fitted = true
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { prefs ->
            val columns = prefs[KEY_GRID_COLUMNS] ?: DEFAULT_COLUMNS
            if (requiredTileRows(defsOf(prefs).fullRowFlags(), columns) <= target) {
                prefs[KEY_GRID_ROWS] = target
            } else {
                fitted = false
            }
        }
        return fitted
    }

    suspend fun setHidden(key: String, hidden: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_APPS] ?: emptySet()
            prefs[KEY_HIDDEN_APPS] = if (hidden) current + key else current - key
        }
    }

    suspend fun setSwipeDownNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SWIPE_DOWN] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.key }
    }

    suspend fun exportBackup(): String {
        val prefs = context.dataStore.data.first()
        return TileCodec.encodeBackup(
            LayoutBackup(
                columns = prefs[KEY_GRID_COLUMNS] ?: DEFAULT_COLUMNS,
                rows = prefs[KEY_GRID_ROWS] ?: DEFAULT_ROWS,
                swipeDownNotifications = prefs[KEY_SWIPE_DOWN] ?: true,
                theme = prefs[KEY_THEME] ?: ThemeMode.SYSTEM.key,
                hidden = (prefs[KEY_HIDDEN_APPS] ?: emptySet()).toList(),
                tiles = defsOf(prefs),
            )
        )
    }

    /** Replaces the whole configuration. @return false when [raw] is not a valid backup. */
    suspend fun importBackup(raw: String): Boolean {
        val backup = TileCodec.decodeBackup(raw) ?: return false
        val columns = backup.columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val needed = requiredTileRows(backup.tiles.fullRowFlags(), columns)
        if (needed > MAX_ROWS) return false
        val rows = maxOf(backup.rows.coerceIn(MIN_ROWS, MAX_ROWS), needed)
        context.dataStore.edit { prefs ->
            prefs[KEY_TILES] = TileCodec.encode(backup.tiles)
            prefs[KEY_GRID_COLUMNS] = columns
            prefs[KEY_GRID_ROWS] = rows
            prefs[KEY_HIDDEN_APPS] = backup.hidden.toSet()
            prefs[KEY_SWIPE_DOWN] = backup.swipeDownNotifications
            prefs[KEY_THEME] = ThemeMode.fromKey(backup.theme).key
        }
        return true
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
}
