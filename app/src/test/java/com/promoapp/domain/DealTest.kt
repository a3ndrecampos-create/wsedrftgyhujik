package com.promoapp.domain

import com.promoapp.testDeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DealTest {
    @Test
    fun `discount is calculated from original price`() {
        assertEquals(50, testDeal(price = 50.0, originalPrice = 100.0).discountPercent)
    }

    @Test
    fun `discount is null without original price`() {
        assertNull(testDeal(price = 50.0, originalPrice = null).discountPercent)
    }

    @Test
    fun `discount is null when price is not lower than original`() {
        assertNull(testDeal(price = 120.0, originalPrice = 100.0).discountPercent)
    }
}
