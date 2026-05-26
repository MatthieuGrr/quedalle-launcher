package dev.mlg.quedalle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mlg.quedalle.model.AppInfo
import dev.mlg.quedalle.model.TileItem
import dev.mlg.quedalle.viewmodel.LauncherViewModel

private val Black     = Color(0xFF000000)
private val CardIdle  = Color(0xFF141414)
private val IdleText  = Color(0xFFE0E0E0)
private val GridPad   = 12.dp
private val GridSpace = 8.dp

private const val ColorAppTile    = 0xFF141414.toInt() // same as app cards
private const val ColorTransparent = 0x00000000        // invisible spacer

// 12 preset tile colors (3 rows × 4 columns in the picker)
private val TileColors: List<Int> = listOf(
    0xFF1C2B3A.toInt(), 0xFF2B1C3A.toInt(), 0xFF1C3A2B.toInt(), 0xFF3A2B1C.toInt(),
    0xFF3A1C1C.toInt(), 0xFF1C3A3A.toInt(), 0xFF2B3A1C.toInt(), 0xFF3A1C2B.toInt(),
    0xFF0F4C81.toInt(), 0xFF6B1F1F.toInt(), 0xFF1A5C40.toInt(), 0xFF7A5A10.toInt(),
)

// ─── Grid layout helpers ──────────────────────────────────────────────────────

private fun gridPositions(tiles: List<TileItem>, columns: Int): List<Pair<Int, Int>> {
    var row = 0; var col = 0
    return tiles.map { tile ->
        Pair(row, col).also {
            if (tile is TileItem.Divider) { row++; col = 0 }
            else { col++; if (col == columns) { col = 0; row++ } }
        }
    }
}

// ─── Entry point ─────────────────────────────────────────────────────────────

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val state          by vm.uiState.collectAsStateWithLifecycle()
    var longPressedTile  by remember { mutableStateOf<TileItem?>(null) }
    var isEditMode       by rememberSaveable { mutableStateOf(false) }
    var showAddSpacer  by remember { mutableStateOf(false) }
    var showAddDivider by remember { mutableStateOf(false) }
    val focusManager     = LocalFocusManager.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshNotificationAccess() }

    BackHandler(enabled = isEditMode || state.isSearching) {
        when {
            isEditMode        -> isEditMode = false
            state.isSearching -> { vm.clearSearch(); focusManager.clearFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            SearchField(
                query = state.searchQuery,
                onQueryChange = vm::onSearchQueryChange,
                onFocusChange = { focused ->
                    if (focused) vm.onSearchActivated() else vm.onSearchDeactivated()
                },
                onDone = { focusManager.clearFocus() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (!state.hasNotificationAccess && !state.isSearching) {
                NotificationBanner(
                    onClick = vm::openNotificationSettings,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
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
                                if (tile is TileItem.App) vm.launchApp(tile.id)
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
                                    when (tile) {
                                        is TileItem.App     -> vm.launchApp(tile.id)
                                        is TileItem.Spacer  -> Unit
                                        is TileItem.Divider -> Unit
                                    }
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
                    onDone          = { isEditMode = false },
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    when (val tile = longPressedTile) {
        is TileItem.App -> AppOptionsDialog(
            app       = tile.info,
            onDismiss   = { longPressedTile = null },
            onTogglePin = { vm.togglePin(tile.id); longPressedTile = null },
            onAppInfo   = { vm.openAppInfo(tile.id); longPressedTile = null },
            onReorder   = { isEditMode = true; longPressedTile = null },
        )
        is TileItem.Spacer -> SpacerDialog(
            initial   = tile.color,
            onConfirm = { color -> vm.updateSpacer(tile.id, color); longPressedTile = null },
            onRemove  = { vm.removeTile(tile.id); longPressedTile = null },
            onDismiss = { longPressedTile = null },
        )
        is TileItem.Divider -> DividerDialog(
            initial   = tile.color,
            onConfirm = { color -> vm.updateDivider(tile.id, color); longPressedTile = null },
            onRemove  = { vm.removeTile(tile.id); longPressedTile = null },
            onDismiss = { longPressedTile = null },
        )
        null -> {}
    }

    if (showAddSpacer) {
        SpacerDialog(
            initial   = TileColors.first(),
            onConfirm = { color -> vm.addSpacer(color); showAddSpacer = false },
            onRemove  = null,
            onDismiss = { showAddSpacer = false },
        )
    }

    if (showAddDivider) {
        DividerDialog(
            initial   = 0xFF2A2A2A.toInt(),
            onConfirm = { color -> vm.addDivider(color); showAddDivider = false },
            onRemove  = null,
            onDismiss = { showAddDivider = false },
        )
    }
}

// ─── Grid ─────────────────────────────────────────────────────────────────────

@Composable
private fun TileGrid(
    tiles: List<TileItem>,
    columns: Int,
    rows: Int?,
    editMode: Boolean,
    onTileClick: (TileItem) -> Unit,
    onTileLongClick: (TileItem) -> Unit,
    modifier: Modifier = Modifier,
    onSaveOrder: ((List<TileItem>) -> Unit)? = null,
) {
    val localTiles = remember { tiles.toMutableStateList() }

    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragAcc     by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(tiles) {
        if (draggingKey == null) {
            localTiles.clear()
            localTiles.addAll(tiles)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current

        val cellWDp = (maxWidth - GridPad * 2 - GridSpace * (columns - 1)) / columns
        val cellHDp: Dp = if (rows != null) {
            (maxHeight - GridSpace * (rows - 1)) / rows
        } else {
            cellWDp / 1.4f
        }

        val cellWPx = with(density) { cellWDp.toPx() }
        val cellHPx = with(density) { cellHDp.toPx() }
        val spacePx = with(density) { GridSpace.toPx() }
        val cellW   = cellWPx + spacePx
        val cellH   = cellHPx + spacePx

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start  = GridPad,
                end    = GridPad,
                bottom = if (rows == null) 16.dp else 0.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(GridSpace),
            verticalArrangement   = Arrangement.spacedBy(GridSpace),
            userScrollEnabled = rows == null,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
            items = localTiles,
            key   = { it.id },
            span  = { if (it is TileItem.Divider) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { tile ->
                val isDragging = draggingKey == tile.id

                val gestureModifier = if (editMode) {
                    Modifier.pointerInput(tile.id, columns) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingKey = tile.id; dragAcc = Offset.Zero },
                            onDrag = { change, delta ->
                                change.consume()
                                dragAcc += delta

                                val from = localTiles.indexOfFirst { it.id == draggingKey }
                                if (from < 0) return@detectDragGesturesAfterLongPress

                                val sx = (dragAcc.x / cellW).toInt()
                                val sy = (dragAcc.y / cellH).toInt()
                                if (sx == 0 && sy == 0) return@detectDragGesturesAfterLongPress

                                val positions = gridPositions(localTiles, columns)
                                val (fromRow, fromCol) = positions[from]
                                val targetRow = fromRow + sy
                                val targetCol = (fromCol + sx).coerceIn(0, columns - 1)
                                val to = positions.indices.minByOrNull { i ->
                                    val (r, c) = positions[i]
                                    val dr = r - targetRow; val dc = c - targetCol
                                    dr * dr + dc * dc
                                } ?: from

                                if (to != from) {
                                    localTiles.add(to, localTiles.removeAt(from))
                                    dragAcc -= Offset(sx * cellW, sy * cellH)
                                }
                            },
                            onDragEnd    = {
                                draggingKey = null
                                onSaveOrder?.invoke(localTiles.toList())
                            },
                            onDragCancel = {
                                draggingKey = null
                                localTiles.clear()
                                localTiles.addAll(tiles)
                            },
                        )
                    }
                } else {
                    Modifier.combinedClickable(
                        onClick     = { onTileClick(tile) },
                        onLongClick = { onTileLongClick(tile) },
                    )
                }

                TileCard(tile, isDragging, cellHDp, gestureModifier)
            }
        }
    }
}

// ─── Tile card visuals ────────────────────────────────────────────────────────

@Composable
private fun TileCard(tile: TileItem, isDragging: Boolean, height: Dp, modifier: Modifier = Modifier) {
    when (tile) {
        is TileItem.App     -> AppCard(tile, isDragging, height, modifier)
        is TileItem.Spacer  -> SpacerCard(tile, isDragging, height, modifier)
        is TileItem.Divider -> DividerCard(tile, isDragging, modifier)
    }
}

@Composable
private fun DividerCard(tile: TileItem.Divider, isDragging: Boolean, modifier: Modifier) {
    val lineColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.secondaryContainer
                      else Color(tile.color),
        animationSpec = tween(if (isDragging) 80 else 200),
        label = "div",
    )
    Box(
        modifier = modifier.fillMaxWidth().height(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GridPad)
                .height(1.dp)
                .background(lineColor),
        )
    }
}

@Composable
private fun AppCard(tile: TileItem.App, isDragging: Boolean, height: Dp, modifier: Modifier) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isDragging                 -> MaterialTheme.colorScheme.secondaryContainer
            tile.info.hasNotification  -> MaterialTheme.colorScheme.primaryContainer
            else                       -> CardIdle
        },
        animationSpec = tween(if (isDragging) 80 else 350),
        label = "bg",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isDragging                 -> MaterialTheme.colorScheme.onSecondaryContainer
            tile.info.hasNotification  -> MaterialTheme.colorScheme.onPrimaryContainer
            else                       -> IdleText
        },
        animationSpec = tween(if (isDragging) 80 else 350),
        label = "text",
    )
    Box(
        modifier = modifier.fillMaxWidth().height(height)
            .clip(RoundedCornerShape(10.dp)).background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tile.info.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SpacerCard(tile: TileItem.Spacer, isDragging: Boolean, height: Dp, modifier: Modifier) {
    val bgColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.secondaryContainer
                      else Color(tile.color),
        animationSpec = tween(if (isDragging) 80 else 200),
        label = "bg",
    )
    Box(
        modifier = modifier.fillMaxWidth().height(height)
            .clip(RoundedCornerShape(10.dp)).background(bgColor),
    )
}

// ─── Color picker ─────────────────────────────────────────────────────────────

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ColorSwatch(ColorTransparent, selected, onSelect, transparent = true)
            ColorSwatch(ColorAppTile,     selected, onSelect)
        }
        TileColors.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { color -> ColorSwatch(color, selected, onSelect) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Int, selected: Int, onSelect: (Int) -> Unit, transparent: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .then(
                if (transparent) Modifier.drawBehind {
                    val cell = size.width / 4
                    for (row in 0..3) for (col in 0..3) drawRect(
                        color     = if ((row + col) % 2 == 0) Color(0xFF2A2A2A) else Color(0xFF555555),
                        topLeft   = Offset(col * cell, row * cell),
                        size      = Size(cell, cell),
                    )
                } else Modifier.background(Color(color))
            )
            .then(
                if (color == selected)
                    Modifier.border(2.dp, Color.White, shape)
                else Modifier
            )
            .clickable { onSelect(color) }
    )
}

// ─── Search field ─────────────────────────────────────────────────────────────

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.onFocusChanged { onFocusChange(it.isFocused) },
        placeholder = { Text("Rechercher…", color = Color(0xFF555555), fontSize = 14.sp) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null,
                tint = Color(0xFF555555), modifier = Modifier.size(18.dp))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor        = Color.White,
            unfocusedTextColor      = Color.White,
            focusedBorderColor      = Color(0xFF2A2A2A),
            unfocusedBorderColor    = Color(0xFF1E1E1E),
            cursorColor             = Color.White,
            focusedContainerColor   = Color(0xFF0E0E0E),
            unfocusedContainerColor = Color(0xFF0A0A0A),
        ),
        shape = RoundedCornerShape(14.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
    )
}

// ─── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun AppOptionsDialog(
    app: AppInfo,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onAppInfo: () -> Unit,
    onReorder: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(app.packageName, fontSize = 10.sp, color = Color(0xFF555555),
                    modifier = Modifier.padding(top = 2.dp))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DialogOption(if (app.isPinned) "Désépingler" else "Épingler", onTogglePin)
                if (app.isPinned) DialogOption("Réorganiser la grille", onReorder)
                DialogOption("Infos de l'application", onAppInfo)
            }
        },
        confirmButton = {},
        dismissButton = {},
        containerColor = Color(0xFF161616),
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun SpacerDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tuile vide", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorPicker(selected) { selected = it }
                if (onRemove != null) {
                    TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                        Text("Supprimer", color = Color(0xFFCC4444), fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        containerColor = Color(0xFF161616),
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun DividerDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Séparateur", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorPicker(selected) { selected = it }
                if (onRemove != null) {
                    TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                        Text("Supprimer", color = Color(0xFFCC4444), fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        containerColor = Color(0xFF161616),
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun DialogOption(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NotificationBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1400))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Activer l'accès aux notifications → couleurs actives",
            color = Color(0xFFBBAA00), fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Text("›", color = Color(0xFFBBAA00), fontSize = 16.sp,
            modifier = Modifier.padding(start = 8.dp))
    }
}

// ─── Edit-mode toolbar ────────────────────────────────────────────────────────

@Composable
private fun EditToolbar(
    columns: Int, rows: Int,
    onColumnsChange: (Int) -> Unit, onRowsChange: (Int) -> Unit,
    onAddSpacer: () -> Unit, onAddDivider: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A)),
    ) {
        // Row 1: add buttons + done
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onAddSpacer,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("+ Vide", fontSize = 12.sp) }

            TextButton(
                onClick = onAddDivider,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("+ Sépar.", fontSize = 12.sp) }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onDone) {
                Text("Terminé", color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }

        // Row 2: grid size controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StepControl("Col", columns, 2, 5,  { onColumnsChange(columns - 1) }, { onColumnsChange(columns + 1) })
            StepControl("Lig", rows,    1, 20, { onRowsChange(rows - 1) },       { onRowsChange(rows + 1) })
        }
    }
}

@Composable
private fun StepControl(
    label: String, value: Int, min: Int, max: Int,
    onDec: () -> Unit, onInc: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF666666), fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onDec, enabled = value > min, modifier = Modifier.size(28.dp)) {
            Text("−", color = if (value > min) Color.White else Color(0xFF333333), fontSize = 16.sp)
        }
        Text("$value", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 20.dp))
        IconButton(onClick = onInc, enabled = value < max, modifier = Modifier.size(28.dp)) {
            Text("+", color = if (value < max) Color.White else Color(0xFF333333), fontSize = 16.sp)
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyHint(isSearching: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = if (isSearching) "Aucun résultat" else "Appui long sur une app pour l'épingler",
            color = Color(0xFF333333), fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}
