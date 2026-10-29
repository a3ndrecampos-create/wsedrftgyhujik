package com.promoapp.domain

import com.promoapp.testDeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyDealPickerTest {

    private fun deal(id: Long, price: Double, original: Double?, postedAt: Long = 0) =
        testDeal(price = price, originalPrice = original).copy(id = id, postedAtMillis = postedAt)

    @Test
    fun `picks the biggest discount`() {
        val deals = listOf(deal(1, 80.0, 100.0), deal(2, 50.0, 100.0), deal(3, 90.0, 100.0))

        assertEquals(2L, DailyDealPicker.pick(deals, lastNotifiedId = null)?.id)
    }

    @Test
    fun `skips the deal that was already notified`() {
        val deals = listOf(deal(1, 80.0, 100.0), deal(2, 50.0, 100.0))

        assertEquals(1L, DailyDealPicker.pick(deals, lastNotifiedId = 2)?.id)
    }

    @Test
    fun `ignores deals below the minimum discount or without original price`() {
        val deals = listOf(deal(1, 90.0, 100.0), deal(2, 50.0, null))

        assertNull(DailyDealPicker.pick(deals, lastNotifiedId = null))
        assertEquals(1L, DailyDealPicker.pick(deals, lastNotifiedId = null, minDiscountPercent = 10)?.id)
    }

    @Test
    fun `ties go to the newest deal`() {
        val deals = listOf(deal(1, 50.0, 100.0, postedAt = 10), deal(2, 50.0, 100.0, postedAt = 20))

        assertEquals(2L, DailyDealPicker.pick(deals, lastNotifiedId = null)?.id)
    }

    @Test
    fun `empty list gives nothing`() {
        assertNull(DailyDealPicker.pick(emptyList(), lastNotifiedId = null))
    }
}
