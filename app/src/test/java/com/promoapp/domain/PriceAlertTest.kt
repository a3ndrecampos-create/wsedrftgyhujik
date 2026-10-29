package com.promoapp.domain

import com.promoapp.domain.model.PriceAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceAlertTest {

    private fun alert(target: Double = 90.0, triggeredAt: Long? = null) = PriceAlert(
        dealId = 1,
        title = "Fone",
        imageUrl = "",
        storeName = "Amazon",
        url = "https://example.com",
        targetPrice = target,
        priceWhenCreated = 100.0,
        lastKnownPrice = 100.0,
        createdAtMillis = 0,
        triggeredAtMillis = triggeredAt,
    )

    @Test
    fun `price above target only updates last known price`() {
        val result = alert().evaluate(currentPrice = 95.0, nowMillis = 10)

        assertEquals(95.0, result.lastKnownPrice, 0.001)
        assertFalse(result.isTriggered)
    }

    @Test
    fun `price at target triggers the alert`() {
        val result = alert().evaluate(currentPrice = 90.0, nowMillis = 10)

        assertTrue(result.isTriggered)
        assertEquals(10L, result.triggeredAtMillis)
    }

    @Test
    fun `price below target triggers the alert`() {
        assertTrue(alert().evaluate(currentPrice = 70.0, nowMillis = 10).isTriggered)
    }

    @Test
    fun `triggered alert is never changed again`() {
        val triggered = alert(triggeredAt = 5)

        assertSame(triggered, triggered.evaluate(currentPrice = 200.0, nowMillis = 99))
    }

    @Test
    fun `active alert has no trigger time`() {
        assertNull(alert().triggeredAtMillis)
    }

    @Test
    fun `suggested target is ten percent below current price`() {
        assertEquals(90.0, PriceAlert.suggestedTarget(100.0), 0.001)
        assertEquals(269.91, PriceAlert.suggestedTarget(299.9), 0.001)
    }

    @Test
    fun `target must be positive and below the current price`() {
        assertTrue(PriceAlert.isValidTarget(target = 80.0, currentPrice = 100.0))
        assertFalse(PriceAlert.isValidTarget(target = 100.0, currentPrice = 100.0))
        assertFalse(PriceAlert.isValidTarget(target = 120.0, currentPrice = 100.0))
        assertFalse(PriceAlert.isValidTarget(target = 0.0, currentPrice = 100.0))
    }
}
