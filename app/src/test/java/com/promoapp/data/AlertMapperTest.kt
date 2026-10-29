package com.promoapp.data

import com.promoapp.data.local.toDomain
import com.promoapp.data.local.toEntity
import com.promoapp.domain.model.PriceAlert
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertMapperTest {

    @Test
    fun `entity round trip keeps every field including trigger time`() {
        val alert = PriceAlert(
            dealId = 7,
            title = "Notebook",
            imageUrl = "https://img",
            storeName = "Magalu",
            url = "https://loja",
            targetPrice = 2_500.0,
            priceWhenCreated = 3_000.0,
            lastKnownPrice = 2_450.0,
            createdAtMillis = 100,
            triggeredAtMillis = 200,
        )

        assertEquals(alert, alert.toEntity().toDomain())
    }

    @Test
    fun `active alert keeps null trigger time`() {
        val alert = PriceAlert(1, "A", "", "Loja", "u", 10.0, 20.0, 20.0, 1, null)

        assertEquals(null, alert.toEntity().toDomain().triggeredAtMillis)
    }
}
