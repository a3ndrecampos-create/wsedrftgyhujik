package com.promoserver.model

import com.promoserver.config.CssSelectors
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Os nomes dos campos seguem o contrato que o app Android já usa (snake_case).

@Serializable
data class DealDto(
    val id: Long,
    val title: String,
    @SerialName("image_url") val imageUrl: String,
    val store: String,
    val price: Double,
    @SerialName("original_price") val originalPrice: Double? = null,
    val author: String,
    @SerialName("posted_at") val postedAt: Long,
    val temperature: Int = 0,
    val comments: Int = 0,
    val tags: List<String> = emptyList(),
    val url: String,
)

@Serializable
data class PricePointDto(
    @SerialName("timestamp") val timestampMillis: Long,
    val price: Double,
)

@Serializable
data class FilterOptionsDto(
    val stores: List<String>,
    val categories: List<String>,
)

@Serializable
data class NewDealRequest(
    val title: String,
    val url: String,
    val store: String,
    val price: Double,
    @SerialName("original_price") val originalPrice: Double? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val coupon: String? = null,
    val category: String? = null,
    @SerialName("free_shipping") val freeShipping: Boolean = false,
    val author: String,
)

@Serializable
data class CouponDto(
    val id: Long,
    val code: String,
    val store: String,
    val description: String,
    @SerialName("discount_label") val discountLabel: String,
    @SerialName("min_purchase") val minPurchase: Double? = null,
    @SerialName("expires_at") val expiresAtMillis: Long? = null,
    val url: String,
)

@Serializable
data class NewCouponRequest(
    val code: String,
    val store: String,
    val description: String,
    @SerialName("discount_label") val discountLabel: String,
    @SerialName("min_purchase") val minPurchase: Double? = null,
    @SerialName("expires_at") val expiresAtMillis: Long? = null,
    val url: String,
)

@Serializable
data class ErrorDto(val error: String, val details: List<String> = emptyList())

@Serializable
data class CollectionReport(
    val collector: String,
    val fetched: Int = 0,
    val accepted: Int = 0,
    val inserted: Int = 0,
    val updated: Int = 0,
    val expired: Int = 0,
    val error: String? = null,
)

@Serializable
data class PreviewRequest(
    val url: String,
    val strategy: String = "auto",
    val css: CssSelectors? = null,
)

@Serializable
data class PreviewProduct(
    val title: String,
    val price: Double,
    @SerialName("original_price") val originalPrice: Double? = null,
    @SerialName("discount_percent") val discountPercent: Int = 0,
    val url: String,
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
data class PreviewResponse(
    val strategy: String,
    val found: Int,
    @SerialName("with_discount") val withDiscount: Int,
    val sample: List<PreviewProduct>,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class RunDto(
    val collector: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("duration_ms") val durationMs: Long,
    val fetched: Int,
    val accepted: Int,
    val inserted: Int,
    val updated: Int,
    val expired: Int,
    val error: String? = null,
)

@Serializable
data class StoreStatDto(
    val store: String,
    @SerialName("active_offers") val activeOffers: Int,
    @SerialName("last_seen_at") val lastSeenAt: Long? = null,
)

@Serializable
data class StatusDto(
    @SerialName("demo_mode") val demoMode: Boolean,
    @SerialName("public_base_url") val publicBaseUrl: String? = null,
    val collectors: List<String>,
    @SerialName("active_offers") val activeOffers: Int,
    @SerialName("inactive_offers") val inactiveOffers: Int,
    @SerialName("user_posts") val userPosts: Int,
    @SerialName("clicks_24h") val clicks24h: Long,
    val stores: List<StoreStatDto>,
    @SerialName("last_runs") val lastRuns: List<RunDto>,
)
