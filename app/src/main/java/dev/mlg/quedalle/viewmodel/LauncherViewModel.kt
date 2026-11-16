package dev.mlg.quedalle.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mlg.quedalle.data.AppPreferences
import dev.mlg.quedalle.data.AppRepository
import dev.mlg.quedalle.data.AppShortcut
import dev.mlg.quedalle.data.DEFAULT_TILE_COLOR
import dev.mlg.quedalle.data.InstalledApps
import dev.mlg.quedalle.data.ProfileState
import dev.mlg.quedalle.data.TYPE_APP
import dev.mlg.quedalle.data.TYPE_DIVIDER
import dev.mlg.quedalle.data.TYPE_INFO
import dev.mlg.quedalle.data.TYPE_LINK
import dev.mlg.quedalle.data.TYPE_SHORTCUT
import dev.mlg.quedalle.data.TYPE_SPACER
import dev.mlg.quedalle.data.TileDef
import dev.mlg.quedalle.model.AppInfo
import dev.mlg.quedalle.model.Gesture
import dev.mlg.quedalle.model.GestureAction
import dev.mlg.quedalle.model.InfoKind
import dev.mlg.quedalle.model.LookPreset
import dev.mlg.quedalle.model.ScreenLook
import dev.mlg.quedalle.model.TileShape
import dev.mlg.quedalle.model.ShortcutTarget
import dev.mlg.quedalle.model.TEXTURE_NONE
import dev.mlg.quedalle.model.ThemeMode
import dev.mlg.quedalle.model.TileItem
import dev.mlg.quedalle.model.TileStyle
import dev.mlg.quedalle.model.TileText
import dev.mlg.quedalle.model.appKey
import dev.mlg.quedalle.model.mergeTileStyle
import dev.mlg.quedalle.model.normalizeLink
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.UUID

private const val TAG = "LauncherViewModel"

data class LauncherUiState(
    val displayedTiles: List<TileItem> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val gridColumns: Int = AppPreferences.DEFAULT_COLUMNS,
    val gridRows: Int = AppPreferences.DEFAULT_ROWS,
    val gestures: Map<Gesture, GestureAction> = emptyMap(),
    val globalStyle: TileStyle = TileStyle(),
    val tileText: TileText = TileText(),
    val tileShape: TileShape = TileShape(),
    val screen: ScreenLook = ScreenLook(),
    val profiles: ProfileState = ProfileState(),
    /** Every launchable app, sorted by label (hidden ones included). */
    val apps: List<AppInfo> = emptyList(),
    val hiddenApps: List<AppInfo> = emptyList(),
    /** Null when the device has no private space. */
    val privateSpaceLocked: Boolean? = null,
    /** True until the installed apps have been read once. */
    val isLoading: Boolean = false,
) {
    fun gesture(gesture: Gesture): GestureAction = gestures[gesture] ?: GestureAction.None
}

enum class UiMessage {
    GRID_FULL, LAUNCH_FAILED, EXPORT_SUCCESS, EXPORT_FAILED, IMPORT_SUCCESS, IMPORT_FAILED,
    SHADE_UNSUPPORTED, PROFILE_LIMIT, LINK_INVALID, CONTACT_FAILED,
    PRIVATE_SPACE_FAILED,
}

/** What to add from the edit toolbar. */
enum class NewTile { SPACER, DIVIDER, CLOCK, DATE, BATTERY, ALARM }

private data class GridConfig(
    val columns: Int,
    val rows: Int,
    val globalStyle: TileStyle,
    val tileText: TileText,
    val tileShape: TileShape,
)
private data class Behavior(
    val gestures: Map<Gesture, GestureAction>,
    val profiles: ProfileState,
    val screen: ScreenLook,
)
private data class SearchState(val query: String, val isActive: Boolean)
private data class Stored(val tiles: List<TileDef>, val hidden: Set<String>)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val repo  = AppRepository(app)

    private val _searchQuery    = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** Null until the first load: "no apps yet" must not read as "nothing pinned". */
    private val installed: StateFlow<InstalledApps?> = repo.apps
        .onEach { pruneUninstalledTiles(it.apps) }
        .map { loaded ->
            val collator = Collator.getInstance()
            loaded.copy(apps = loaded.apps.sortedWith(compareBy(collator) { it.label }))
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val gridConfig: Flow<GridConfig> = combine(
        prefs.gridColumns, prefs.gridRows, prefs.globalStyle, prefs.tileText, prefs.tileShape,
    ) { cols, rows, global, text, shape -> GridConfig(cols, rows, global, text, shape) }

    /** Needed by the theme itself, before any screen state exists. */
    val screenLook: StateFlow<ScreenLook> = prefs.screenLook
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScreenLook())

    private val behavior: Flow<Behavior> = combine(prefs.gestures, prefs.profiles, prefs.screenLook, ::Behavior)

    private val searchState: Flow<SearchState> = combine(_searchQuery, _isSearchActive) { q, a ->
        SearchState(q, a)
    }

    private val stored: Flow<Stored> = combine(prefs.tileDefinitions, prefs.hiddenApps, ::Stored)

    val uiState: StateFlow<LauncherUiState> = combine(
        installed,
        stored,
        searchState,
        gridConfig,
        behavior,
    ) { loaded, stored, search, config, behavior ->
        val apps = loaded?.apps ?: emptyList()
        val hidden = stored.hidden
        val appMap  = apps.associateBy { it.key }
        val appDefs = stored.tiles.filter { it.type == TYPE_APP }.associateBy { it.id }

        fun overrideOf(def: TileDef?) = TileStyle(def?.color, def?.textColor, def?.texture)
        fun styleOf(def: TileDef?) = mergeTileStyle(overrideOf(def), config.globalStyle)

        val displayedTiles = if (search.isActive) {
            // Pinned apps keep their real style and custom label in results,
            // so the tile sheet previews (and edits) what actually exists.
            apps.filter { it.key !in hidden }
                .mapNotNull { app ->
                    val def = appDefs[app.key]
                    val rank = listOfNotNull(
                        searchRank(app.label, search.query),
                        def?.label?.let { searchRank(it, search.query) },
                    ).minOrNull()
                    rank?.let { Triple(it, app, def) }
                }
                .sortedBy { it.first }
                .map { (_, app, def) ->
                    TileItem.App(
                        info = app.copy(isPinned = def != null, customLabel = def?.label),
                        style = styleOf(def),
                        override = overrideOf(def),
                    )
                }
        } else {
            stored.tiles.mapNotNull { def ->
                when (def.type) {
                    TYPE_APP -> appMap[def.id]?.let { app ->
                        TileItem.App(
                            info = app.copy(isPinned = true, customLabel = def.label),
                            style = styleOf(def),
                            override = overrideOf(def),
                        )
                    }
                    // A shortcut lives and dies with its app: hidden while
                    // the app is unreachable (locked profile, private space).
                    TYPE_SHORTCUT -> appMap[appKey(def.pkg.orEmpty(), def.userSerial)]?.let { app ->
                        TileItem.Shortcut(
                            id = def.id,
                            label = def.label ?: app.label,
                            target = ShortcutTarget.AppShortcut(
                                app.packageName, def.userSerial, def.shortcutId.orEmpty(),
                            ),
                            style = styleOf(def),
                            override = overrideOf(def),
                        )
                    }
                    TYPE_LINK -> TileItem.Shortcut(
                        id = def.id,
                        label = def.label ?: def.uri.orEmpty(),
                        target = ShortcutTarget.Link(def.uri.orEmpty()),
                        style = styleOf(def),
                        override = overrideOf(def),
                    )
                    TYPE_INFO -> InfoKind.fromKey(def.kind)?.let { kind ->
                        TileItem.Info(def.id, kind, styleOf(def), overrideOf(def))
                    }
                    TYPE_SPACER -> TileItem.Spacer(
                        def.id,
                        def.color ?: DEFAULT_TILE_COLOR,
                        def.texture?.takeIf { it != TEXTURE_NONE },
                    )
                    TYPE_DIVIDER -> TileItem.Divider(def.id, def.color ?: DEFAULT_TILE_COLOR)
                    else -> null
                }
            }
        }

        LauncherUiState(
            displayedTiles = displayedTiles,
            searchQuery = search.query,
            isSearching = search.isActive,
            gridColumns = config.columns,
            gridRows    = config.rows,
            gestures = behavior.gestures,
            globalStyle = config.globalStyle,
            tileText = config.tileText,
            tileShape = config.tileShape,
            screen = behavior.screen,
            profiles = behavior.profiles,
            apps = apps,
            hiddenApps = apps.filter { it.key in hidden },
            privateSpaceLocked = loaded?.privateSpaceLocked,
            isLoading = loaded == null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState(isLoading = true),
    )

    /**
     * Drops the tiles (and shortcut tiles) of apps that were uninstalled,
     * in every layout. Without this they stay in storage, invisible, and
     * still take up room in the grid.
     */
    private suspend fun pruneUninstalledTiles(apps: List<AppInfo>) {
        try {
            val installed = apps.mapTo(HashSet()) { it.key }
            fun appKeyOf(def: TileDef): String? = when (def.type) {
                TYPE_APP -> def.id
                TYPE_SHORTCUT -> appKey(def.pkg.orEmpty(), def.userSerial)
                else -> null
            }
            val gone = prefs.allTileDefinitions()
                .filter { def -> appKeyOf(def)?.let { it !in installed } == true }
                .filter { repo.isUninstalled(it.pkg ?: it.id, it.userSerial) }
                .mapNotNullTo(HashSet(), ::appKeyOf)
            if (gone.isNotEmpty()) prefs.removeTilesEverywhere { appKeyOf(it) in gone }
        } catch (e: Exception) {
            Log.e(TAG, "Pruning uninstalled tiles failed", e)
        }
    }

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
        launchLogged { prefs.reorderTiles(tiles.map { it.id }) }
    }

    fun addTile(kind: NewTile, spacerColor: Int, dividerColor: Int) {
        val def = when (kind) {
            NewTile.SPACER  -> TileDef(TYPE_SPACER, "sp_${UUID.randomUUID()}", color = spacerColor)
            NewTile.DIVIDER -> TileDef(TYPE_DIVIDER, "dv_${UUID.randomUUID()}", color = dividerColor)
            NewTile.CLOCK   -> infoDef(InfoKind.CLOCK)
            NewTile.DATE    -> infoDef(InfoKind.DATE)
            NewTile.BATTERY -> infoDef(InfoKind.BATTERY)
            NewTile.ALARM   -> infoDef(InfoKind.ALARM)
        }
        launchChecked { prefs.addTile(def) }
    }

    private fun infoDef(kind: InfoKind) = TileDef(TYPE_INFO, "in_${UUID.randomUUID()}", kind = kind.key)

    /** Adds a tile opening [address] (a web page, "tel:", "mailto:"…). */
    fun addLink(label: String, address: String) {
        val uri = normalizeLink(address)
        if (uri == null) {
            _messages.tryEmit(UiMessage.LINK_INVALID)
            return
        }
        val name = label.trim().ifEmpty { uri.substringAfter("://").removePrefix("www.").trimEnd('/') }
        launchChecked { prefs.addTile(TileDef(TYPE_LINK, "ln_${UUID.randomUUID()}", label = name, uri = uri)) }
    }

    /** Adds a tile opening the contact the user just picked. */
    fun addContact(picked: Uri) {
        viewModelScope.launch {
            try {
                val contact = withContext(Dispatchers.IO) { repo.contactLink(picked) }
                when {
                    contact == null -> _messages.emit(UiMessage.CONTACT_FAILED)
                    !prefs.addTile(
                        TileDef(TYPE_LINK, "ln_${UUID.randomUUID()}", label = contact.first, uri = contact.second)
                    ) -> _messages.emit(UiMessage.GRID_FULL)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Adding a contact tile failed", e)
            }
        }
    }

    /** No app answered the contact picker. */
    fun reportNoContactsApp() {
        _messages.tryEmit(UiMessage.CONTACT_FAILED)
    }

    /** Shortcuts [app] offers for pinning; empty unless Quedalle is the default launcher. */
    suspend fun shortcutsOf(app: AppInfo): List<AppShortcut> =
        withContext(Dispatchers.IO) { repo.shortcutsOf(app) }

    fun addShortcut(app: AppInfo, shortcut: AppShortcut) {
        viewModelScope.launch {
            try {
                val def = TileDef(
                    TYPE_SHORTCUT, "sc_${UUID.randomUUID()}",
                    pkg = app.packageName, userSerial = app.userSerial,
                    label = shortcut.label, shortcutId = shortcut.id,
                )
                if (prefs.addTile(def)) syncShortcutPins(app.packageName, app.userSerial)
                else _messages.emit(UiMessage.GRID_FULL)
            } catch (e: Exception) {
                Log.e(TAG, "Adding a shortcut tile failed", e)
            }
        }
    }

    /** Keeps the system's pinned-shortcut list of a package equal to our tiles. */
    private suspend fun syncShortcutPins(packageName: String, userSerial: Long?) {
        val ids = prefs.allTileDefinitions()
            .filter { it.type == TYPE_SHORTCUT && it.pkg == packageName && it.userSerial == userSerial }
            .mapNotNull { it.shortcutId }
            .distinct()
        withContext(Dispatchers.IO) { repo.pinShortcuts(packageName, userSerial, ids) }
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
    fun setTileText(text: TileText)        { launchLogged { prefs.setTileText(text) } }
    fun setTileShape(shape: TileShape)     { launchLogged { prefs.setTileShape(shape) } }
    fun setHaptics(enabled: Boolean)       { launchLogged { prefs.setHaptics(enabled) } }
    fun applyPreset(preset: LookPreset)    { launchLogged { prefs.applyPreset(preset) } }
    fun setScreenBackground(dark: Boolean, color: Int?) {
        launchLogged { prefs.setScreenBackground(dark, color) }
    }

    fun removeTile(id: String) {
        launchLogged {
            val def = prefs.tileDefinitions.first().firstOrNull { it.id == id }
            prefs.removeTile(id)
            if (def?.type == TYPE_SHORTCUT && def.pkg != null) syncShortcutPins(def.pkg, def.userSerial)
        }
    }

    /** Renames a tile; null restores the original app label. */
    fun renameTile(id: String, label: String?) {
        launchLogged { prefs.updateTile(id) { it.copy(label = label?.takeIf(String::isNotBlank)) } }
    }

    fun setGridColumns(columns: Int) = launchChecked { prefs.setGridColumns(columns) }
    fun setGridRows(rows: Int)       = launchChecked { prefs.setGridRows(rows) }

    // ── Layouts ──────────────────────────────────────────────────────────────
    fun createProfile(name: String) {
        launchLogged { if (!prefs.createProfile(name)) _messages.emit(UiMessage.PROFILE_LIMIT) }
    }
    fun switchProfile(id: String)               { launchLogged { prefs.switchProfile(id) } }
    fun renameProfile(id: String, name: String) { launchLogged { prefs.renameProfile(id, name) } }
    fun deleteProfile(id: String)               { launchLogged { prefs.deleteProfile(id) } }

    // ── Hidden apps ──────────────────────────────────────────────────────────
    fun hideApp(app: AppInfo)   { launchLogged { prefs.setHidden(app.key, true) } }
    fun unhideApp(app: AppInfo) { launchLogged { prefs.setHidden(app.key, false) } }

    // ── Settings ─────────────────────────────────────────────────────────────
    fun setGesture(gesture: Gesture, action: GestureAction) {
        launchLogged { prefs.setGesture(gesture, action) }
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

    // ── Launching ────────────────────────────────────────────────────────────
    fun launchApp(app: AppInfo) {
        if (!repo.launchApp(app)) _messages.tryEmit(UiMessage.LAUNCH_FAILED)
        if (_isSearchActive.value) clearSearch()
    }

    /** A tap on a home-screen tile. */
    fun openTile(tile: TileItem) {
        val opened = when (tile) {
            is TileItem.App -> return launchApp(tile.info)
            is TileItem.Shortcut -> when (val target = tile.target) {
                is ShortcutTarget.AppShortcut ->
                    repo.startShortcut(target.packageName, target.userSerial, target.shortcutId)
                is ShortcutTarget.Link -> repo.openLink(target.uri)
            }
            is TileItem.Info -> repo.openInfo(tile.kind)
            is TileItem.Spacer, is TileItem.Divider -> true
        }
        if (!opened) _messages.tryEmit(UiMessage.LAUNCH_FAILED)
    }

    fun openAppInfo(app: AppInfo) = repo.openAppInfo(app)

    fun requestUninstall(app: AppInfo) = repo.requestUninstall(app)

    fun togglePrivateSpace() {
        if (!repo.togglePrivateSpace()) _messages.tryEmit(UiMessage.PRIVATE_SPACE_FAILED)
    }

    /**
     * Runs what a gesture is bound to. [GestureAction.Search] is the one
     * action the screen handles itself (it owns the search field).
     */
    fun runGestureAction(action: GestureAction) {
        when (action) {
            GestureAction.None, GestureAction.Search -> Unit
            GestureAction.Notifications ->
                if (!repo.expandStatusBar(quickSettings = false)) _messages.tryEmit(UiMessage.SHADE_UNSUPPORTED)
            GestureAction.QuickSettings ->
                if (!repo.expandStatusBar(quickSettings = true)) _messages.tryEmit(UiMessage.SHADE_UNSUPPORTED)
            GestureAction.NextProfile -> launchLogged { prefs.nextProfile() }
            GestureAction.PrivateSpace -> togglePrivateSpace()
            is GestureAction.LaunchApp -> {
                val app = installed.value?.apps?.firstOrNull { it.key == action.appKey }
                if (app != null) launchApp(app) else _messages.tryEmit(UiMessage.LAUNCH_FAILED)
            }
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
