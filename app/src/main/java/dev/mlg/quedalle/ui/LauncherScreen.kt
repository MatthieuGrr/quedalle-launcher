package dev.mlg.quedalle.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mlg.quedalle.R
import dev.mlg.quedalle.model.Gesture
import dev.mlg.quedalle.model.GestureAction
import dev.mlg.quedalle.model.TileItem
import dev.mlg.quedalle.ui.theme.LocalQuedallePalette
import dev.mlg.quedalle.ui.theme.LocalTileText
import dev.mlg.quedalle.ui.theme.QuedalleColors
import dev.mlg.quedalle.viewmodel.LauncherUiState
import dev.mlg.quedalle.viewmodel.LauncherViewModel
import dev.mlg.quedalle.viewmodel.UiMessage

/** Vertical travel after which a swipe counts as a gesture. */
private val SwipeThreshold = 100.dp

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalTileText provides state.tileText) {
        LauncherContent(vm, state)
    }
}

@Composable
private fun LauncherContent(vm: LauncherViewModel, state: LauncherUiState) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    var sheetTileId   by remember { mutableStateOf<String?>(null) }
    var showHomeSheet by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var isEditMode    by rememberSaveable { mutableStateOf(false) }
    var showSettings  by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
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
    val contactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri -> uri?.let(vm::addContact) }

    BackHandler(enabled = showSettings || isEditMode || state.isSearching) {
        when {
            showSettings      -> showSettings = false
            isEditMode        -> isEditMode = false
            state.isSearching -> { vm.clearSearch(); focusManager.clearFocus() }
        }
    }

    // Focusing the search field is the one gesture action the screen owns.
    fun runGesture(action: GestureAction) {
        if (action == GestureAction.Search) {
            try { searchFocus.requestFocus() } catch (_: IllegalStateException) { }
        } else {
            vm.runGestureAction(action)
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
                state = state,
                themeMode = themeMode,
                vm = vm,
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
                    focusRequester = searchFocus,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )

                val gesturesEnabled = !state.isSearching && !isEditMode
                val swipeDown = state.gesture(Gesture.SWIPE_DOWN)
                val swipeUp = state.gesture(Gesture.SWIPE_UP)
                val doubleTap = state.gesture(Gesture.DOUBLE_TAP)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(gesturesEnabled, swipeDown, swipeUp) {
                            if (!gesturesEnabled) return@pointerInput
                            if (swipeDown == GestureAction.None && swipeUp == GestureAction.None) return@pointerInput
                            var total = 0f
                            var fired = false
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f; fired = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    total += dragAmount
                                    val threshold = SwipeThreshold.toPx()
                                    if (!fired && total > threshold) {
                                        fired = true
                                        runGesture(swipeDown)
                                    } else if (!fired && total < -threshold) {
                                        fired = true
                                        runGesture(swipeUp)
                                    }
                                },
                            )
                        }
                        // Taps on a tile are consumed by the tile: these only
                        // fire on the empty space around and between tiles.
                        .pointerInput(gesturesEnabled, doubleTap) {
                            if (!gesturesEnabled) return@pointerInput
                            detectTapGestures(
                                onDoubleTap = if (doubleTap == GestureAction.None) null
                                              else { _ -> runGesture(doubleTap) },
                                onLongPress = { showHomeSheet = true },
                            )
                        },
                ) {
                    when {
                        // Apps not read yet: stay blank rather than claim
                        // that nothing is pinned.
                        state.isLoading -> Unit

                        state.displayedTiles.isEmpty() ->
                            EmptyHint(state.isSearching, Modifier.fillMaxSize())

                        state.isSearching ->
                            TileGrid(
                                tiles = state.displayedTiles,
                                columns = state.gridColumns,
                                rows = null,
                                editMode = false,
                                onTileClick = { tile ->
                                    if (tile is TileItem.App) {
                                        vm.launchApp(tile.info)
                                        focusManager.clearFocus()
                                    }
                                },
                                onTileLongClick = { sheetTileId = it.id },
                                modifier = Modifier.fillMaxSize(),
                            )

                        else ->
                            // The grid keeps local drag state: start afresh
                            // when edit mode toggles or the layout changes.
                            key(isEditMode, state.profiles.activeId) {
                                TileGrid(
                                    tiles = state.displayedTiles,
                                    columns = state.gridColumns,
                                    rows = state.gridRows,
                                    editMode = isEditMode,
                                    onTileClick = vm::openTile,
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
                        onAdd = { vm.addTile(it, QuedalleColors.TilePresets.first(), QuedalleColors.DividerDefault) },
                        onAddLink    = { showLinkDialog = true },
                        onAddContact = {
                            try { contactLauncher.launch(null) } catch (_: Exception) { vm.reportNoContactsApp() }
                        },
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

    if (showLinkDialog) {
        LinkDialog(
            onConfirm = vm::addLink,
            onDismiss = { showLinkDialog = false },
        )
    }

    // ── Tile sheet — everything about a tile in one place, applied live ──────
    val sheetTile = sheetTileId?.let { id -> state.displayedTiles.firstOrNull { it.id == id } }
    LaunchedEffect(sheetTile == null) {
        if (sheetTile == null) sheetTileId = null
    }
    if (sheetTile != null || showHomeSheet) {
        fun close() { sheetTileId = null; showHomeSheet = false }
        TileSheet(
            tile = sheetTile,
            state = state,
            vm = vm,
            onEnterEdit = {
                vm.clearSearch()
                focusManager.clearFocus()
                isEditMode = true
                close()
            },
            onOpenSettings = {
                vm.clearSearch()
                focusManager.clearFocus()
                showSettings = true
                close()
            },
            onDismiss = { close() },
        )
    }
}

private val UiMessage.stringRes: Int
    get() = when (this) {
        UiMessage.GRID_FULL      -> R.string.msg_grid_full
        UiMessage.LAUNCH_FAILED  -> R.string.msg_launch_failed
        UiMessage.EXPORT_SUCCESS -> R.string.msg_export_success
        UiMessage.EXPORT_FAILED  -> R.string.msg_export_failed
        UiMessage.IMPORT_SUCCESS -> R.string.msg_import_success
        UiMessage.IMPORT_FAILED  -> R.string.msg_import_failed
        UiMessage.SHADE_UNSUPPORTED -> R.string.msg_shade_unsupported
        UiMessage.PROFILE_LIMIT -> R.string.msg_profile_limit
        UiMessage.LINK_INVALID -> R.string.msg_link_invalid
        UiMessage.CONTACT_FAILED -> R.string.msg_contact_failed
        UiMessage.PRIVATE_SPACE_FAILED -> R.string.msg_private_space_failed
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

/** Asks for the address (and optional name) of a link tile. */
@Composable
private fun LinkDialog(onConfirm: (label: String, address: String) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalQuedallePalette.current
    var address by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        containerColor = palette.surface,
        shape = RoundedCornerShape(16.dp),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tile_link), color = palette.textStrong, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_address)) },
                    placeholder = { Text("example.org") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_name_optional)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = address.isNotBlank(),
                onClick = { onDismiss(); onConfirm(label, address) },
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
