package dev.mlg.quedalle.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

data class TileDef(
    val type: String,              // "app" | "spacer" | "divider"
    val id: String,
    val pkg: String?   = null,
    val label: String? = null,
    val color: Int     = 0xFF141414.toInt(),
)

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_ORDERED_PINS = stringPreferencesKey("ordered_pins") // legacy — read only
        private val KEY_TILES        = stringPreferencesKey("tiles")
        private val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        private val KEY_GRID_ROWS    = intPreferencesKey("grid_rows")
    }

    val tileDefinitions: Flow<List<TileDef>> = context.dataStore.data.map { prefs ->
        parseTileDefs(prefs[KEY_TILES])
            ?: prefs[KEY_ORDERED_PINS]
                ?.split(",")?.filter { it.isNotEmpty() }
                ?.map { TileDef("app", it, pkg = it) }
            ?: emptyList()
    }

    val gridColumns: Flow<Int> = context.dataStore.data.map { it[KEY_GRID_COLUMNS] ?: 3 }
    val gridRows: Flow<Int>    = context.dataStore.data.map { it[KEY_GRID_ROWS]    ?: 4 }

    suspend fun togglePin(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = currentDefs(prefs)
            val exists  = current.any { it.type == "app" && it.pkg == packageName }
            prefs[KEY_TILES] = (
                if (exists) current.filter { it.id != packageName }
                else current + TileDef("app", packageName, pkg = packageName)
            ).toJson()
        }
    }

    suspend fun saveTiles(defs: List<TileDef>) {
        context.dataStore.edit { it[KEY_TILES] = defs.toJson() }
    }

    suspend fun addTile(def: TileDef) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TILES] = (currentDefs(prefs) + def).toJson()
        }
    }

    suspend fun removeTile(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TILES] = currentDefs(prefs).filter { it.id != id }.toJson()
        }
    }

    suspend fun updateTile(updated: TileDef) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TILES] = currentDefs(prefs).map { if (it.id == updated.id) updated else it }.toJson()
        }
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { it[KEY_GRID_COLUMNS] = columns.coerceIn(2, 5) }
    }

    suspend fun setGridRows(rows: Int) {
        context.dataStore.edit { it[KEY_GRID_ROWS] = rows.coerceIn(1, 20) }
    }

    private fun currentDefs(prefs: Preferences): List<TileDef> =
        parseTileDefs(prefs[KEY_TILES])
            ?: prefs[KEY_ORDERED_PINS]
                ?.split(",")?.filter { it.isNotEmpty() }
                ?.map { TileDef("app", it, pkg = it) }
            ?: emptyList()
}

// ─── JSON helpers ─────────────────────────────────────────────────────────────

private fun List<TileDef>.toJson(): String {
    val arr = JSONArray()
    for (def in this) {
        val obj = JSONObject().put("t", def.type).put("id", def.id)
        def.pkg?.let   { obj.put("pkg", it) }
        def.label?.let { obj.put("l",   it) }
        if (def.type != "app") obj.put("c", def.color)  // spacer / divider
        arr.put(obj)
    }
    return arr.toString()
}

private fun parseTileDefs(json: String?): List<TileDef>? {
    json ?: return null
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TileDef(
                type  = o.getString("t"),
                id    = o.getString("id"),
                pkg   = o.optString("pkg").ifEmpty { null },
                label = o.optString("l").ifEmpty   { null },
                color = o.optInt("c", 0xFF141414.toInt()),
            )
        }
    } catch (_: Exception) { null }
}
