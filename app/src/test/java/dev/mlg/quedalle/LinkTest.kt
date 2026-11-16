package dev.mlg.quedalle

import dev.mlg.quedalle.model.normalizeLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkTest {

    @Test
    fun `a bare address becomes https`() {
        assertEquals("https://example.org/a?b=1", normalizeLink("  example.org/a?b=1 "))
    }

    @Test
    fun `an address with a scheme is kept`() {
        assertEquals("http://example.org", normalizeLink("http://example.org"))
        assertEquals("tel:+33123456789", normalizeLink("tel:+33123456789"))
        assertEquals("mailto:a@b.c", normalizeLink("mailto:a@b.c"))
        assertEquals("geo:48.85,2.35", normalizeLink("geo:48.85,2.35"))
    }

    @Test
    fun `nothing openable gives null`() {
        assertNull(normalizeLink("   "))
        assertNull(normalizeLink("two words"))
    }
}
