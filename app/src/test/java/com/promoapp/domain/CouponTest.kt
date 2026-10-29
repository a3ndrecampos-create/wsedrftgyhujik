package com.promoapp.domain

import com.promoapp.domain.model.Coupon
import com.promoapp.domain.model.CouponStatus
import com.promoapp.domain.model.sortedForDisplay
import org.junit.Assert.assertEquals
import org.junit.Test

class CouponTest {

    private val now = 1_000_000_000L
    private val hour = 3_600_000L

    private fun coupon(id: Long = 1, expiresAt: Long?) = Coupon(
        id = id,
        code = "CODE$id",
        storeName = "Loja",
        description = "Descrição",
        discountLabel = "10% OFF",
        minPurchase = null,
        expiresAtMillis = expiresAt,
        url = "https://example.com",
    )

    @Test
    fun `coupon without expiry is active`() {
        assertEquals(CouponStatus.ACTIVE, coupon(expiresAt = null).statusAt(now))
    }

    @Test
    fun `coupon is expired at and after the expiry instant`() {
        assertEquals(CouponStatus.EXPIRED, coupon(expiresAt = now).statusAt(now))
        assertEquals(CouponStatus.EXPIRED, coupon(expiresAt = now - 1).statusAt(now))
    }

    @Test
    fun `coupon within 24 hours is expiring soon`() {
        assertEquals(CouponStatus.EXPIRING_SOON, coupon(expiresAt = now + 23 * hour).statusAt(now))
        assertEquals(CouponStatus.EXPIRING_SOON, coupon(expiresAt = now + 24 * hour).statusAt(now))
    }

    @Test
    fun `coupon beyond 24 hours is active`() {
        assertEquals(CouponStatus.ACTIVE, coupon(expiresAt = now + 25 * hour).statusAt(now))
    }

    @Test
    fun `display order is expiring soon, active, then expired`() {
        val expired = coupon(id = 1, expiresAt = now - hour)
        val active = coupon(id = 2, expiresAt = now + 5 * 24 * hour)
        val noExpiry = coupon(id = 3, expiresAt = null)
        val expiringSoon = coupon(id = 4, expiresAt = now + 3 * hour)

        val result = listOf(expired, noExpiry, active, expiringSoon).sortedForDisplay(now)

        assertEquals(listOf(4L, 2L, 3L, 1L), result.map { it.id })
    }
}
