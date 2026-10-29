package com.promoapp.domain.model

data class PriceAlert(
    val dealId: Long,
    val title: String,
    val imageUrl: String,
    val storeName: String,
    val url: String,
    val targetPrice: Double,
    val priceWhenCreated: Double,
    val lastKnownPrice: Double,
    val createdAtMillis: Long,
    val triggeredAtMillis: Long?,
) {
    val isTriggered: Boolean get() = triggeredAtMillis != null

    /** Aplica o preço atual: atualiza o último preço visto e marca o alerta como atingido se chegou ao alvo. */
    fun evaluate(currentPrice: Double, nowMillis: Long): PriceAlert = when {
        isTriggered -> this
        currentPrice <= targetPrice -> copy(lastKnownPrice = currentPrice, triggeredAtMillis = nowMillis)
        else -> copy(lastKnownPrice = currentPrice)
    }

    companion object {
        private const val SUGGESTED_DISCOUNT_FACTOR = 0.9

        /** Sugestão inicial: 10% abaixo do preço atual. */
        fun suggestedTarget(currentPrice: Double): Double =
            Math.round(currentPrice * SUGGESTED_DISCOUNT_FACTOR * 100) / 100.0

        /** O alvo precisa ser positivo e menor que o preço atual, senão o alerta não faria sentido. */
        fun isValidTarget(target: Double, currentPrice: Double): Boolean = target > 0 && target < currentPrice
    }
}
