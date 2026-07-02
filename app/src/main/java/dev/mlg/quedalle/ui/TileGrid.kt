package dev.mlg.quedalle.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mlg.quedalle.model.TileItem
import dev.mlg.quedalle.model.fullRowFlags
import dev.mlg.quedalle.model.gridPositions
import dev.mlg.quedalle.ui.theme.QuedalleColors

internal val GridPad   = 12.dp
internal val GridSpace = 8.dp

@Composable
fun TileGrid(
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
    val currentTiles by rememberUpdatedState(tiles)

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

                                val positions = gridPositions(localTiles.fullRowFlags(), columns)
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
                                val validIds = currentTiles.map { it.id }.toSet()
                                onSaveOrder?.invoke(localTiles.filter { it.id in validIds })
                            },
                            onDragCancel = {
                                draggingKey = null
                                localTiles.clear()
                                localTiles.addAll(currentTiles)
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
        targetValue = if (isDragging) MaterialTheme.colorScheme.secondaryContainer
                      else QuedalleColors.CardIdle,
        animationSpec = tween(if (isDragging) 80 else 350),
        label = "bg",
    )
    val textColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.onSecondaryContainer
                      else QuedalleColors.TextPrimary,
        animationSpec = tween(if (isDragging) 80 else 350),
        label = "text",
    )
    Box(
        modifier = modifier.fillMaxWidth().height(height)
            .clip(RoundedCornerShape(10.dp)).background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tile.info.displayLabel,
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
