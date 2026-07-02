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

/** Number of grid rows needed to display all items without scrolling. */
fun requiredRows(isFullRow: List<Boolean>, columns: Int): Int {
    if (isFullRow.isEmpty()) return 0
    return gridPositions(isFullRow, columns).last().first + 1
}
