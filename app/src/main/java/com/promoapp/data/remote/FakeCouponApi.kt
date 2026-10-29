package com.promoapp.data.remote

import kotlinx.coroutines.delay

/** Gera 12 cupons com validades variadas: expirado, vencendo em horas, em dias e sem data. */
class FakeCouponApi : CouponApi {

    private val stores = listOf("Amazon", "Mercado Livre", "Magalu", "Shopee", "KaBuM!")
    private val prefixes = listOf("PROMO", "ECONOMIZE", "BEMVINDO", "FRETEJA")
    private val discounts = listOf("10% OFF", "R$ 50 OFF", "15% OFF", "Frete grátis")
    private val descriptions = listOf(
        "Válido em itens selecionados",
        "Primeira compra pelo app",
        "Em toda a loja, exceto eletrônicos",
        "Para pagamentos no Pix",
    )

    override suspend fun getCoupons(): List<CouponDto> {
        delay(NETWORK_DELAY_MS)
        val now = System.currentTimeMillis()
        return List(COUPON_COUNT) { index ->
            val id = index + 1L
            CouponDto(
                id = id,
                code = "${prefixes[index % prefixes.size]}${10 + id}",
                storeName = stores[index % stores.size],
                description = descriptions[index % descriptions.size],
                discountLabel = discounts[index % discounts.size],
                minPurchase = if (index % 2 == 0) null else 150.0 + index * 10,
                expiresAtMillis = when (index % 4) {
                    0 -> now - HOUR_MS
                    1 -> now + 3 * HOUR_MS
                    2 -> now + 5 * DAY_MS
                    else -> null
                },
                url = "https://example.com/cupom/$id",
            )
        }
    }

    private companion object {
        const val NETWORK_DELAY_MS = 500L
        const val COUPON_COUNT = 12
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
    }
}
