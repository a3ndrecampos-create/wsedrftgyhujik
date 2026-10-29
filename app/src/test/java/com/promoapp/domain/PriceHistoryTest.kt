package com.promoapp.domain

import com.promoapp.domain.model.PriceHistory
import com.promoapp.domain.model.PricePoint
import com.promoapp.domain.model.PriceVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceHistoryTest {

    private val history = PriceHistory(
        listOf(100.0, 200.0, 300.0).mapIndexed { index, price -> PricePoint(index.toLong(), price) },
    )

    @Test
    fun `statistics are computed from points`() {
        assertEquals(100.0, history.lowest!!, 0.001)
        assertEquals(300.0, history.highest!!, 0.001)
        assertEquals(200.0, history.average!!, 0.001)
    }

    @Test
    fun `verdict is null without history`() {
        assertNull(PriceHistory(emptyList()).verdictFor(50.0))
    }

    @Test
    fun `price at or below minimum is lowest ever`() {
        assertEquals(PriceVerdict.LOWEST_EVER, history.verdictFor(100.0))
        assertEquals(PriceVerdict.LOWEST_EVER, history.verdictFor(80.0))
    }

    @Test
    fun `price well below average is good`() {
        assertEquals(PriceVerdict.GOOD_PRICE, history.verdictFor(150.0))
    }

    @Test
    fun `price near average is fair`() {
        assertEquals(PriceVerdict.FAIR_PRICE, history.verdictFor(200.0))
    }

    @Test
    fun `price above average is flagged`() {
        assertEquals(PriceVerdict.ABOVE_AVERAGE, history.verdictFor(250.0))
    }
}
