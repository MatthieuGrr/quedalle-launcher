package dev.mlg.quedalle.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mlg.quedalle.R
import dev.mlg.quedalle.model.TileItem
import dev.mlg.quedalle.ui.theme.LocalQuedallePalette
import dev.mlg.quedalle.ui.theme.QuedalleColors
import dev.mlg.quedalle.viewmodel.LauncherViewModel
import dev.mlg.quedalle.viewmodel.UiMessage

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    var sheetTileId   by remember { mutableStateOf<String?>(null) }
    var showHomeMenu  by remember { mutableStateOf(false) }
    var isEditMode    by rememberSaveable { mutableStateOf(false) }
    var showSettings  by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshApps() }

    LaunchedEffect(resources) {
        vm.messages.collect { message ->
            snackbarHostState.showSnackbar(resources.getString(message.stringRes))
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importFrom) }

    BackHandler(enabled = showSettings || isEditMode || state.isSearching) {
        when {
            showSettings      -> showSettings = false
            isEditMode        -> isEditMode = false
            state.isSearching -> { vm.clearSearch(); focusManager.clearFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalQuedallePalette.current.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        if (showSettings) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = vm::setThemeMode,
                globalStyle = state.globalStyle,
                onGlobalBackground = vm::setGlobalBackground,
                onGlobalTextColor = vm::setGlobalTextColor,
                onGlobalTexture = vm::setGlobalTexture,
                onApplyGlobalToAll = vm::applyGlobalStyleToAllTiles,
                swipeDownNotifications = state.swipeDownNotifications,
                onSwipeDownChange = vm::setSwipeDownNotifications,
                hiddenApps = state.hiddenApps,
                onUnhide = vm::unhideApp,
                onExport = { exportLauncher.launch("quedalle-backup.json") },
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onBack = { showSettings = false },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {

                SearchField(
                    query = state.searchQuery,
                    onQueryChange = vm::onSearchQueryChange,
                    onFocusChange = { focused ->
                        if (focused) vm.onSearchActivated() else vm.onSearchDeactivated()
                    },
                    onDone = {
                        vm.launchFirstResult()
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )

                val onHome = !state.isSearching && !isEditMode
                val swipeEnabled = state.swipeDownNotifications && onHome
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(swipeEnabled) {
                            if (!swipeEnabled) return@pointerInput
                            var total = 0f
                            var fired = false
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f; fired = false },
                                onVerticalDrag = { _, dragAmount ->
                                    total += dragAmount
                                    if (!fired && total > 100.dp.toPx()) {
                                        fired = true
                                        vm.openNotificationShade()
                                    }
                                },
                            )
                        }
                        .pointerInput(onHome) {
                            if (!onHome) return@pointerInput
                            // Long-press on empty home space opens the home menu.
                            detectTapGestures(onLongPress = { showHomeMenu = true })
                        },
                ) {
                    when {
                        state.displayedTiles.isEmpty() ->
                            EmptyHint(state.isSearching, Modifier.fillMaxSize())

                        state.isSearching ->
                            TileGrid(
                                tiles = state.displayedTiles,
                                columns = state.gridColumns,
                                rows = null,
                                editMode = false,
                                onTileClick = { tile ->
                                    if (tile is TileItem.App) vm.launchApp(tile.info)
                                },
                                onTileLongClick = { sheetTileId = it.id },
                                modifier = Modifier.fillMaxSize(),
                            )

                        else ->
                            key(isEditMode) {
                                TileGrid(
                                    tiles = state.displayedTiles,
                                    columns = state.gridColumns,
                                    rows = state.gridRows,
                                    editMode = isEditMode,
                                    onTileClick = { tile ->
                                        if (tile is TileItem.App) vm.launchApp(tile.info)
                                    },
                                    onTileLongClick = { sheetTileId = it.id },
                                    onSaveOrder = vm::saveTileOrder,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                    }
                }

                if (isEditMode) {
                    EditToolbar(
                        columns = state.gridColumns,
                        rows = state.gridRows,
                        onColumnsChange = vm::setGridColumns,
                        onRowsChange    = vm::setGridRows,
                        onAddSpacer  = { vm.addSpacer(QuedalleColors.TilePresets.first(), null) },
                        onAddDivider = { vm.addDivider(QuedalleColors.DividerDefault) },
                        onDone       = { isEditMode = false },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ── Tile sheet — everything about a tile in one place, applied live ──────
    val sheetTile = sheetTileId?.let { id -> state.displayedTiles.firstOrNull { it.id == id } }
    LaunchedEffect(sheetTile == null) {
        if (sheetTile == null) sheetTileId = null
    }
    sheetTile?.let { tile ->
        TileSheet(
            tile = tile,
            vm = vm,
            onEnterEdit = {
                vm.clearSearch()
                focusManager.clearFocus()
                isEditMode = true
                sheetTileId = null
            },
            onDismiss = { sheetTileId = null },
        )
    }

    if (showHomeMenu) {
        HomeSheet(
            onReorder  = { isEditMode = true; showHomeMenu = false },
            onSettings = { showSettings = true; showHomeMenu = false },
            onDismiss  = { showHomeMenu = false },
        )
    }
}

private val UiMessage.stringRes: Int
    get() = when (this) {
        UiMessage.GRID_FULL      -> R.string.msg_grid_full
        UiMessage.EXPORT_SUCCESS -> R.string.msg_export_success
        UiMessage.EXPORT_FAILED  -> R.string.msg_export_failed
        UiMessage.IMPORT_SUCCESS -> R.string.msg_import_success
        UiMessage.IMPORT_FAILED  -> R.string.msg_import_failed
    }

@Composable
private fun EmptyHint(isSearching: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(if (isSearching) R.string.empty_search else R.string.empty_hint),
            color = LocalQuedallePalette.current.textFaint, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}
