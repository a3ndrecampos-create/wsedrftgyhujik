package com.promoapp.ui

import com.promoapp.ui.search.formatPriceInput
import com.promoapp.ui.search.parsePrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceInputTest {
    @Test
    fun `comma and dot are both accepted`() {
        assertEquals(12.5, parsePrice("12,50")!!, 0.001)
        assertEquals(12.5, parsePrice("12.5")!!, 0.001)
    }

    @Test
    fun `blank, invalid and negative inputs return null`() {
        assertNull(parsePrice(""))
        assertNull(parsePrice("abc"))
        assertNull(parsePrice("-5"))
    }

    @Test
    fun `whole numbers are formatted without decimals`() {
        assertEquals("100", formatPriceInput(100.0))
        assertEquals("99.9", formatPriceInput(99.9))
    }
}
