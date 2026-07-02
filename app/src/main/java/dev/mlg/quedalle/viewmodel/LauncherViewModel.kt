package dev.mlg.quedalle.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mlg.quedalle.data.AppPreferences
import dev.mlg.quedalle.data.AppRepository
import dev.mlg.quedalle.data.DEFAULT_TILE_COLOR
import dev.mlg.quedalle.data.TYPE_APP
import dev.mlg.quedalle.data.TYPE_DIVIDER
import dev.mlg.quedalle.data.TYPE_SPACER
import dev.mlg.quedalle.data.TileDef
import dev.mlg.quedalle.model.TEXTURE_NONE
import dev.mlg.quedalle.model.mergeTileStyle
import dev.mlg.quedalle.model.AppInfo
import dev.mlg.quedalle.model.ThemeMode
import dev.mlg.quedalle.model.TileItem
import dev.mlg.quedalle.model.TileStyle
import dev.mlg.quedalle.model.searchRank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.UUID

private const val TAG = "LauncherViewModel"

data class LauncherUiState(
    val displayedTiles: List<TileItem> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val gridColumns: Int = AppPreferences.DEFAULT_COLUMNS,
    val gridRows: Int = AppPreferences.DEFAULT_ROWS,
    val swipeDownNotifications: Boolean = true,
    val globalStyle: TileStyle = TileStyle(),
    val hiddenApps: List<AppInfo> = emptyList(),
)

enum class UiMessage { GRID_FULL, EXPORT_SUCCESS, EXPORT_FAILED, IMPORT_SUCCESS, IMPORT_FAILED }

private data class GridConfig(
    val columns: Int,
    val rows: Int,
    val swipeDown: Boolean,
    val globalStyle: TileStyle,
)
private data class SearchState(val query: String, val isActive: Boolean)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val repo  = AppRepository(app)

    private val _searchQuery    = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    private val allApps: StateFlow<List<AppInfo>> = repo.apps
        .map { apps ->
            val collator = Collator.getInstance()
            apps.sortedWith(compareBy(collator) { it.label })
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val gridConfig: Flow<GridConfig> = combine(
        prefs.gridColumns, prefs.gridRows, prefs.swipeDownNotifications, prefs.globalStyle
    ) { cols, rows, swipe, global -> GridConfig(cols, rows, swipe, global) }

    private val searchState: Flow<SearchState> = combine(_searchQuery, _isSearchActive) { q, a ->
        SearchState(q, a)
    }

    val uiState: StateFlow<LauncherUiState> = combine(
        allApps,
        prefs.tileDefinitions,
        prefs.hiddenApps,
        searchState,
        gridConfig,
    ) { apps, tileDefs, hidden, search, config ->
        val appMap     = apps.associateBy { it.key }
        val pinnedKeys = tileDefs.filter { it.type == TYPE_APP }.map { it.id }.toSet()

        val displayedTiles = if (search.isActive) {
            apps.filter { it.key !in hidden }
                .mapNotNull { app -> searchRank(app.label, search.query)?.let { rank -> rank to app } }
                .sortedBy { it.first }
                .map { (_, app) -> TileItem.App(app.copy(isPinned = app.key in pinnedKeys)) }
        } else {
            tileDefs.mapNotNull { def ->
                when (def.type) {
                    TYPE_APP     -> appMap[def.id]?.let { app ->
                        val override = TileStyle(def.color, def.textColor, def.texture)
                        TileItem.App(
                            info = app.copy(isPinned = true, customLabel = def.label),
                            style = mergeTileStyle(override, config.globalStyle),
                            override = override,
                        )
                    }
                    TYPE_SPACER  -> TileItem.Spacer(
                        def.id,
                        def.color ?: DEFAULT_TILE_COLOR,
                        def.texture?.takeIf { it != TEXTURE_NONE },
                    )
                    TYPE_DIVIDER -> TileItem.Divider(def.id, def.color ?: DEFAULT_TILE_COLOR)
                    else         -> null
                }
            }
        }

        LauncherUiState(
            displayedTiles = displayedTiles,
            searchQuery = search.query,
            isSearching = search.isActive,
            gridColumns = config.columns,
            gridRows    = config.rows,
            swipeDownNotifications = config.swipeDown,
            globalStyle = config.globalStyle,
            hiddenApps = apps.filter { it.key in hidden },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState(),
    )

    fun refreshApps() = repo.refresh()

    // ── Search ───────────────────────────────────────────────────────────────
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        // Typing always engages search, even if the field kept its focus
        // across an app launch (no focus event would re-activate it).
        if (query.isNotEmpty()) _isSearchActive.value = true
    }
    fun onSearchActivated()   { _isSearchActive.value = true }
    fun onSearchDeactivated() { _isSearchActive.value = false }
    fun clearSearch() {
        _searchQuery.value = ""
        _isSearchActive.value = false
    }

    /** Launches the first search result (keyboard "Done" action). */
    fun launchFirstResult() {
        val first = uiState.value.displayedTiles.firstOrNull() as? TileItem.App ?: return
        launchApp(first.info)
    }

    // ── Tiles ────────────────────────────────────────────────────────────────
    fun togglePin(app: AppInfo) {
        launchChecked { prefs.togglePin(app.key, app.packageName, app.userSerial) }
    }

    fun saveTileOrder(tiles: List<TileItem>) {
        launchLogged { prefs.saveTiles(tiles.map { it.toDef() }) }
    }

    fun addSpacer(color: Int, texture: String?) {
        launchChecked {
            prefs.addTile(TileDef(TYPE_SPACER, "sp_${UUID.randomUUID()}", color = color, texture = texture))
        }
    }

    fun addDivider(color: Int) {
        launchChecked { prefs.addTile(TileDef(TYPE_DIVIDER, "dv_${UUID.randomUUID()}", color = color)) }
    }

    fun setTileBackground(id: String, color: Int) {
        launchLogged { prefs.updateTile(id) { it.copy(color = color) } }
    }

    /** [TEXT_COLOR_AUTO] forces automatic contrast for this tile. */
    fun setTileTextColor(id: String, color: Int) {
        launchLogged { prefs.updateTile(id) { it.copy(textColor = color) } }
    }

    /** [TEXTURE_NONE] forces a flat tile. */
    fun setTileTexture(id: String, texture: String) {
        launchLogged { prefs.updateTile(id) { it.copy(texture = texture) } }
    }

    /** Clears all style overrides: the tile follows the global style again. */
    fun resetTileStyle(id: String) {
        launchLogged {
            prefs.updateTile(id) { it.copy(color = null, textColor = null, texture = null) }
        }
    }

    // ── Global tile style ────────────────────────────────────────────────────
    fun setGlobalBackground(color: Int)    { launchLogged { prefs.setGlobalBackground(color) } }
    fun setGlobalTextColor(color: Int?)    { launchLogged { prefs.setGlobalTextColor(color) } }
    fun setGlobalTexture(texture: String?) { launchLogged { prefs.setGlobalTexture(texture) } }
    fun applyGlobalStyleToAllTiles()       { launchLogged { prefs.applyGlobalStyleToAllTiles() } }

    fun removeTile(id: String) {
        launchLogged { prefs.removeTile(id) }
    }

    /** Renames a pinned tile; null restores the original app label. */
    fun renameTile(id: String, label: String?) {
        launchLogged { prefs.updateTile(id) { it.copy(label = label?.takeIf(String::isNotBlank)) } }
    }

    fun setGridColumns(columns: Int) = launchChecked { prefs.setGridColumns(columns) }
    fun setGridRows(rows: Int)       = launchChecked { prefs.setGridRows(rows) }

    // ── Hidden apps ──────────────────────────────────────────────────────────
    fun hideApp(app: AppInfo)   { launchLogged { prefs.setHidden(app.key, true) } }
    fun unhideApp(app: AppInfo) { launchLogged { prefs.setHidden(app.key, false) } }

    // ── Settings ─────────────────────────────────────────────────────────────
    fun setSwipeDownNotifications(enabled: Boolean) {
        launchLogged { prefs.setSwipeDownNotifications(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        launchLogged { prefs.setThemeMode(mode) }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = prefs.exportBackup()
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?.use { it.write(data.toByteArray()) }
                    ?: error("Cannot open $uri for writing")
                _messages.emit(UiMessage.EXPORT_SUCCESS)
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _messages.emit(UiMessage.EXPORT_FAILED)
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val raw = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().decodeToString() }
                    ?: error("Cannot open $uri for reading")
                _messages.emit(if (prefs.importBackup(raw)) UiMessage.IMPORT_SUCCESS else UiMessage.IMPORT_FAILED)
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _messages.emit(UiMessage.IMPORT_FAILED)
            }
        }
    }

    // ── App actions ──────────────────────────────────────────────────────────
    fun launchApp(app: AppInfo) {
        repo.launchApp(app)
        if (_isSearchActive.value) clearSearch()
    }

    fun openAppInfo(app: AppInfo) = repo.openAppInfo(app)

    fun requestUninstall(app: AppInfo) = repo.requestUninstall(app)

    /** Opens the system notification shade (used by the swipe-down gesture). */
    @SuppressLint("WrongConstant", "PrivateApi")
    fun openNotificationShade() {
        try {
            val service = getApplication<Application>().getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(service)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot expand notification shade", e)
        }
    }

    private fun launchLogged(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Preference update failed", e)
            }
        }
    }

    private fun launchChecked(block: suspend () -> Boolean) {
        viewModelScope.launch {
            try {
                if (!block()) _messages.emit(UiMessage.GRID_FULL)
            } catch (e: Exception) {
                Log.e(TAG, "Preference update failed", e)
            }
        }
    }
}

private fun TileItem.toDef() = when (this) {
    is TileItem.App     -> TileDef(
        TYPE_APP, id,
        pkg = info.packageName, userSerial = info.userSerial, label = info.customLabel,
        color = override.background, textColor = override.textColor, texture = override.texture,
    )
    is TileItem.Spacer  -> TileDef(TYPE_SPACER,  id, color = color, texture = texture)
    is TileItem.Divider -> TileDef(TYPE_DIVIDER, id, color = color)
}
