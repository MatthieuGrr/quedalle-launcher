package dev.mlg.quedalle

import dev.mlg.quedalle.model.gridPositions
import dev.mlg.quedalle.model.requiredTileRows
import org.junit.Assert.assertEquals
import org.junit.Test

class GridMathTest {

    private val tile = false
    private val divider = true

    @Test
    fun `empty grid needs no rows`() {
        assertEquals(0, requiredTileRows(emptyList(), 3))
    }

    @Test
    fun `tiles fill rows left to right`() {
        val positions = gridPositions(List(5) { tile }, 3)
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1), positions)
        assertEquals(2, requiredTileRows(List(5) { tile }, 3))
    }

    @Test
    fun `exact multiple of columns`() {
        assertEquals(2, requiredTileRows(List(6) { tile }, 3))
        assertEquals(3, requiredTileRows(List(7) { tile }, 3))
    }

    @Test
    fun `dividers do not consume grid rows`() {
        // Dividers are thin: 6 tiles + a divider still fit a 3x2 grid.
        assertEquals(2, requiredTileRows(listOf(tile, tile, tile, divider, tile, tile, tile), 3))
        assertEquals(0, requiredTileRows(listOf(divider, divider), 3))
        assertEquals(1, requiredTileRows(listOf(divider, tile, tile, tile), 3))
    }

    @Test
    fun `divider mid-row wraps to its own row`() {
        // 2 tiles on row 0, divider wraps to row 1, remaining tile on row 2
        val flags = listOf(tile, tile, divider, tile)
        val positions = gridPositions(flags, 3)
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0, 2 to 0), positions)
        // Only rows 0 and 2 hold tiles: 2 full-height rows needed.
        assertEquals(2, requiredTileRows(flags, 3))
    }

    @Test
    fun `full grid with dividers between every row`() {
        val flags = listOf(tile, tile, divider, tile, tile, divider, tile, tile)
        assertEquals(3, requiredTileRows(flags, 2))
    }

    @Test
    fun `single column grid`() {
        assertEquals(3, requiredTileRows(List(3) { tile }, 1))
    }
}
