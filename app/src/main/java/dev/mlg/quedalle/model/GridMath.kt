package dev.mlg.quedalle.model

/**
 * Computes the (row, column) of each item in a grid where some items
 * (dividers) span the full row. Mirrors how LazyVerticalGrid places
 * full-span items: a full-row item started mid-row wraps to the next row.
 */
fun gridPositions(isFullRow: List<Boolean>, columns: Int): List<Pair<Int, Int>> {
    var row = 0
    var col = 0
    return isFullRow.map { fullRow ->
        if (fullRow) {
            if (col > 0) { row++; col = 0 }
            Pair(row, 0).also { row++ }
        } else {
            Pair(row, col).also {
                col++
                if (col == columns) { col = 0; row++ }
            }
        }
    }
}

/**
 * Number of full-height grid rows needed to display all items without
 * scrolling. Dividers are thin and don't consume a grid row: only rows
 * containing at least one regular tile count against the capacity.
 */
fun requiredTileRows(isFullRow: List<Boolean>, columns: Int): Int {
    val positions = gridPositions(isFullRow, columns)
    return positions.indices
        .filterNot { isFullRow[it] }
        .map { positions[it].first }
        .distinct()
        .size
}
