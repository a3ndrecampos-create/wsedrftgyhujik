package com.promoapp.data.remote

import com.promoapp.domain.model.Coupon
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CouponDto(
    val id: Long,
    val code: String,
    @SerialName("store") val storeName: String,
    val description: String,
    @SerialName("discount_label") val discountLabel: String,
    @SerialName("min_purchase") val minPurchase: Double? = null,
    @SerialName("expires_at") val expiresAtMillis: Long? = null,
    val url: String,
)

fun CouponDto.toDomain() =
    Coupon(
        id = id,
        code = code,
        storeName = storeName,
        description = description,
        discountLabel = discountLabel,
        minPurchase = minPurchase,
        expiresAtMillis = expiresAtMillis,
        url = url,
    )
