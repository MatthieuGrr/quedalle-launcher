package dev.mlg.quedalle.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
    var longPressedTile by remember { mutableStateOf<TileItem?>(null) }
    var renamingTile    by remember { mutableStateOf<TileItem.App?>(null) }
    var isEditMode      by rememberSaveable { mutableStateOf(false) }
    var showSettings    by rememberSaveable { mutableStateOf(false) }
    var showAddSpacer   by remember { mutableStateOf(false) }
    var showAddDivider  by remember { mutableStateOf(false) }
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

                val swipeEnabled = state.swipeDownNotifications && !state.isSearching && !isEditMode
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
                                onTileLongClick = { longPressedTile = it },
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
                                    onTileLongClick = { longPressedTile = it },
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
                        onAddSpacer  = { showAddSpacer = true; showAddDivider = false },
                        onAddDivider = { showAddDivider = true; showAddSpacer = false },
                        onSettings   = { showSettings = true },
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

    // ── Dialogs ───────────────────────────────────────────────────────────────
    when (val tile = longPressedTile) {
        is TileItem.App -> {
            val app = tile.info
            AppOptionsDialog(
                app = app,
                onDismiss   = { longPressedTile = null },
                onTogglePin = { vm.togglePin(app); longPressedTile = null },
                onRename    = if (app.isPinned) {
                    { renamingTile = tile; longPressedTile = null }
                } else null,
                onReorder   = if (app.isPinned) {
                    {
                        vm.clearSearch()
                        focusManager.clearFocus()
                        isEditMode = true
                        longPressedTile = null
                    }
                } else null,
                onHide      = if (!app.isPinned) {
                    { vm.hideApp(app); longPressedTile = null }
                } else null,
                onUninstall = if (!app.isSystemApp) {
                    { vm.requestUninstall(app); longPressedTile = null }
                } else null,
                onAppInfo   = { vm.openAppInfo(app); longPressedTile = null },
                onSettings  = {
                    vm.clearSearch()
                    focusManager.clearFocus()
                    showSettings = true
                    longPressedTile = null
                },
            )
        }
        is TileItem.Spacer -> TileColorDialog(
            titleRes  = R.string.dialog_spacer_title,
            initial   = tile.color,
            onConfirm = { color -> vm.updateTileColor(tile.id, color); longPressedTile = null },
            onRemove  = { vm.removeTile(tile.id); longPressedTile = null },
            onDismiss = { longPressedTile = null },
        )
        is TileItem.Divider -> TileColorDialog(
            titleRes  = R.string.dialog_divider_title,
            initial   = tile.color,
            onConfirm = { color -> vm.updateTileColor(tile.id, color); longPressedTile = null },
            onRemove  = { vm.removeTile(tile.id); longPressedTile = null },
            onDismiss = { longPressedTile = null },
        )
        null -> {}
    }

    renamingTile?.let { tile ->
        RenameDialog(
            current  = tile.info.customLabel ?: tile.info.label,
            original = tile.info.label,
            onConfirm = { newLabel ->
                vm.renameTile(tile.id, newLabel.takeIf { it != tile.info.label })
                renamingTile = null
            },
            onDismiss = { renamingTile = null },
        )
    }

    if (showAddSpacer) {
        TileColorDialog(
            titleRes  = R.string.dialog_spacer_title,
            initial   = QuedalleColors.TilePresets.first(),
            onConfirm = { color -> vm.addSpacer(color); showAddSpacer = false },
            onRemove  = null,
            onDismiss = { showAddSpacer = false },
        )
    }

    if (showAddDivider) {
        TileColorDialog(
            titleRes  = R.string.dialog_divider_title,
            initial   = QuedalleColors.DividerDefault,
            onConfirm = { color -> vm.addDivider(color); showAddDivider = false },
            onRemove  = null,
            onDismiss = { showAddDivider = false },
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
