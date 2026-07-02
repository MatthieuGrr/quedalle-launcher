package dev.mlg.quedalle

import dev.mlg.quedalle.model.normalizeForSearch
import dev.mlg.quedalle.model.searchRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTest {

    @Test
    fun `normalization strips diacritics and case`() {
        assertEquals("telephone", normalizeForSearch("Téléphone"))
        assertEquals("cafe creme", normalizeForSearch("Café Crème"))
        assertEquals("uber", normalizeForSearch("Über"))
    }

    @Test
    fun `accent-insensitive matching`() {
        assertEquals(0, searchRank("Téléphone", "tele"))
        assertEquals(0, searchRank("Telephone", "télé"))
    }

    @Test
    fun `prefix ranks before word-prefix, before substring`() {
        assertEquals(0, searchRank("Maps", "ma"))
        assertEquals(1, searchRank("Google Maps", "ma"))
        assertEquals(2, searchRank("Grammalecte", "ma"))
    }

    @Test
    fun `no match returns null`() {
        assertNull(searchRank("Calculator", "zz"))
    }

    @Test
    fun `blank query matches everything`() {
        assertEquals(2, searchRank("Anything", ""))
        assertEquals(2, searchRank("Anything", "  "))
    }
}
