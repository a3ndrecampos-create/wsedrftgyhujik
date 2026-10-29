package com.promoserver.model

/** Oferta "crua" vinda de um coletor, antes de limpeza e de salvar no banco. */
data class Offer(
    val store: String,
    val externalId: String,
    val title: String,
    val imageUrl: String,
    val price: Double,
    val originalPrice: Double?,
    val url: String,
    val category: String? = null,
    val freeShipping: Boolean = false,
    val couponCode: String? = null,
) {
    val discountPercent: Int
        get() = originalPrice?.takeIf { it > price && it > 0 }?.let { (((it - price) / it) * 100).toInt() } ?: 0
}

fun Offer.tags(): List<String> = buildList {
    if (freeShipping) add("Frete Grátis")
    couponCode?.takeIf { it.isNotBlank() }?.let { add("Cupom $it") }
}
