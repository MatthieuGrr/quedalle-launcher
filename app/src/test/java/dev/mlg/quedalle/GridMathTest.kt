package dev.mlg.quedalle

import dev.mlg.quedalle.model.gridPositions
import dev.mlg.quedalle.model.requiredRows
import org.junit.Assert.assertEquals
import org.junit.Test

class GridMathTest {

    private val tile = false
    private val divider = true

    @Test
    fun `empty grid needs no rows`() {
        assertEquals(0, requiredRows(emptyList(), 3))
    }

    @Test
    fun `tiles fill rows left to right`() {
        val positions = gridPositions(List(5) { tile }, 3)
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1), positions)
        assertEquals(2, requiredRows(List(5) { tile }, 3))
    }

    @Test
    fun `exact multiple of columns`() {
        assertEquals(2, requiredRows(List(6) { tile }, 3))
        assertEquals(3, requiredRows(List(7) { tile }, 3))
    }

    @Test
    fun `divider at row start takes one row`() {
        // divider, then 3 tiles in 3 columns
        assertEquals(2, requiredRows(listOf(divider, tile, tile, tile), 3))
    }

    @Test
    fun `divider mid-row wraps to its own row`() {
        // 2 tiles on row 0, divider wraps to row 1, remaining tile on row 2
        val flags = listOf(tile, tile, divider, tile)
        val positions = gridPositions(flags, 3)
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0, 2 to 0), positions)
        assertEquals(3, requiredRows(flags, 3))
    }

    @Test
    fun `consecutive dividers stack`() {
        assertEquals(3, requiredRows(listOf(divider, divider, tile), 2))
    }

    @Test
    fun `single column grid`() {
        assertEquals(3, requiredRows(List(3) { tile }, 1))
    }
}
