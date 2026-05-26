package dev.mlg.quedalle.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mlg.quedalle.data.AppPreferences
import dev.mlg.quedalle.data.AppRepository
import dev.mlg.quedalle.data.TileDef
import dev.mlg.quedalle.model.AppInfo
import dev.mlg.quedalle.model.TileItem
import dev.mlg.quedalle.service.LauncherNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class LauncherUiState(
    val displayedTiles: List<TileItem> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val gridColumns: Int = 3,
    val gridRows: Int = 4,
    val hasNotificationAccess: Boolean = true,
)

private data class GridConfig(val columns: Int, val rows: Int, val hasNotifAccess: Boolean)
private data class SearchState(val query: String, val isActive: Boolean)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val repo  = AppRepository(app)

    private val _searchQuery    = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _allApps        = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _hasNotifAccess = MutableStateFlow(isNotificationServiceEnabled())

    private var refreshJob: Job? = null
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshJob?.cancel()
            refreshJob = viewModelScope.launch(Dispatchers.IO) { _allApps.value = repo.getInstalledApps() }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) { _allApps.value = repo.getInstalledApps() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(packageReceiver, filter)
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }

    private val gridConfig: Flow<GridConfig> = combine(
        prefs.gridColumns, prefs.gridRows, _hasNotifAccess
    ) { cols, rows, notif -> GridConfig(cols, rows, notif) }

    private val searchState: Flow<SearchState> = combine(_searchQuery, _isSearchActive) { q, a ->
        SearchState(q, a)
    }

    val uiState: StateFlow<LauncherUiState> = combine(
        _allApps,
        prefs.tileDefinitions,
        LauncherNotificationListener.notifiedPackages,
        searchState,
        gridConfig,
    ) { allApps, tileDefs, notifs, search, config ->
        val appMap     = allApps.associateBy { it.packageName }
        val pinnedPkgs = tileDefs.filter { it.type == "app" }.mapNotNull { it.pkg }.toSet()

        if (search.isActive) {
            val nonPinned = allApps.filter { it.packageName !in pinnedPkgs }
            val results   = if (search.query.isBlank()) nonPinned
                            else nonPinned.filter { it.label.contains(search.query, ignoreCase = true) }
            LauncherUiState(
                displayedTiles = results.map { app ->
                    TileItem.App(app.copy(hasNotification = app.packageName in notifs))
                },
                searchQuery = search.query,
                isSearching = true,
                gridColumns = config.columns,
                gridRows    = config.rows,
                hasNotificationAccess = config.hasNotifAccess,
            )
        } else {
            LauncherUiState(
                displayedTiles = tileDefs.mapNotNull { def ->
                    when (def.type) {
                        "app"     -> appMap[def.pkg]?.let { app ->
                            TileItem.App(app.copy(isPinned = true, hasNotification = app.packageName in notifs))
                        }
                        "spacer"  -> TileItem.Spacer(def.id, def.color)
                        "divider" -> TileItem.Divider(def.id, def.color)
                        else      -> null
                    }
                },
                searchQuery = search.query,
                isSearching = false,
                gridColumns = config.columns,
                gridRows    = config.rows,
                hasNotificationAccess = config.hasNotifAccess,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState(hasNotificationAccess = isNotificationServiceEnabled()),
    )

    // ── Search ───────────────────────────────────────────────────────────────
    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onSearchActivated()   { _isSearchActive.value = true }
    fun onSearchDeactivated() { _isSearchActive.value = false }
    fun clearSearch() {
        _searchQuery.value = ""
        _isSearchActive.value = false
    }

    // ── Tiles ────────────────────────────────────────────────────────────────
    fun togglePin(packageName: String) { launchPrefs { prefs.togglePin(packageName) } }

    fun saveTileOrder(tiles: List<TileItem>) {
        launchPrefs { prefs.saveTiles(tiles.map { it.toDef() }) }
    }

    fun addSpacer(color: Int) {
        launchPrefs { prefs.addTile(TileDef("spacer", "sp_${UUID.randomUUID()}", color = color)) }
    }

    fun addDivider(color: Int) {
        launchPrefs { prefs.addTile(TileDef("divider", "dv_${UUID.randomUUID()}", color = color)) }
    }

    fun updateDivider(id: String, color: Int) {
        launchPrefs { prefs.updateTile(TileDef("divider", id, color = color)) }
    }

    fun removeTile(id: String) { launchPrefs { prefs.removeTile(id) } }

    fun updateSpacer(id: String, color: Int) {
        launchPrefs { prefs.updateTile(TileDef("spacer", id, color = color)) }
    }

    // ── App actions ──────────────────────────────────────────────────────────
    fun launchApp(packageName: String) {
        val intent = getApplication<Application>().packageManager
            .getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        getApplication<Application>().startActivity(intent)
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun refreshNotificationAccess() { _hasNotifAccess.value = isNotificationServiceEnabled() }
    fun setGridColumns(columns: Int) { launchPrefs { prefs.setGridColumns(columns) } }
    fun setGridRows(rows: Int)       { launchPrefs { prefs.setGridRows(rows) } }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val cn = ComponentName(getApplication(), LauncherNotificationListener::class.java).flattenToString()
        return flat.contains(cn)
    }

    private fun launchPrefs(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}

private fun TileItem.toDef() = when (this) {
    is TileItem.App     -> TileDef("app",     id, pkg = id)
    is TileItem.Spacer  -> TileDef("spacer",  id, color = color)
    is TileItem.Divider -> TileDef("divider", id, color = color)
}
