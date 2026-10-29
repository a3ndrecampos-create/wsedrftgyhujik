package com.promoapp.domain.model

data class PricePoint(
    val timestampMillis: Long,
    val price: Double,
)

enum class PriceVerdict {
    LOWEST_EVER,
    GOOD_PRICE,
    FAIR_PRICE,
    ABOVE_AVERAGE,
}

data class PriceHistory(
    val points: List<PricePoint>,
) {
    val lowest: Double? get() = points.minOfOrNull { it.price }
    val highest: Double? get() = points.maxOfOrNull { it.price }
    val average: Double? get() = points.takeIf { it.isNotEmpty() }?.map { it.price }?.average()

    /** Compara o preço atual com o histórico. Retorna null quando não há histórico. */
    fun verdictFor(currentPrice: Double): PriceVerdict? {
        val min = lowest ?: return null
        val avg = average ?: return null
        return when {
            currentPrice <= min -> PriceVerdict.LOWEST_EVER
            currentPrice <= avg * GOOD_PRICE_FACTOR -> PriceVerdict.GOOD_PRICE
            currentPrice <= avg * FAIR_PRICE_FACTOR -> PriceVerdict.FAIR_PRICE
            else -> PriceVerdict.ABOVE_AVERAGE
        }
    }

    private companion object {
        const val GOOD_PRICE_FACTOR = 0.90
        const val FAIR_PRICE_FACTOR = 1.05
    }
}

data class DealDetail(
    val deal: Deal,
    val history: PriceHistory,
) {
    val verdict: PriceVerdict? get() = history.verdictFor(deal.price)
}
