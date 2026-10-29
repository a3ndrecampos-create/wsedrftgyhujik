package com.promoapp.domain.model

data class Deal(
    val id: Long,
    val title: String,
    val imageUrl: String,
    val storeName: String,
    val price: Double,
    val originalPrice: Double?,
    val authorName: String,
    val postedAtMillis: Long,
    val temperature: Int,
    val commentCount: Int,
    val tags: List<String>,
    val url: String,
) {
    /** Percentual de desconto calculado a partir do preço original, ou null se não houver. */
    val discountPercent: Int?
        get() = originalPrice
            ?.takeIf { it > price && it > 0 }
            ?.let { (((it - price) / it) * 100).toInt() }
}

enum class DealTab {
    FOR_YOU,
    TRENDING,
    RECENT,
    LOWEST_PRICE,
}
