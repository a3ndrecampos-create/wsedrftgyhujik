package com.promoapp.domain.model

/** A ordem dos valores define a ordem de exibição: o que vence logo vem primeiro, expirados por último. */
enum class CouponStatus {
    EXPIRING_SOON,
    ACTIVE,
    EXPIRED,
}

data class Coupon(
    val id: Long,
    val code: String,
    val storeName: String,
    val description: String,
    val discountLabel: String,
    val minPurchase: Double?,
    val expiresAtMillis: Long?,
    val url: String,
) {
    fun statusAt(nowMillis: Long): CouponStatus {
        val expiresAt = expiresAtMillis ?: return CouponStatus.ACTIVE
        return when {
            nowMillis >= expiresAt -> CouponStatus.EXPIRED
            expiresAt - nowMillis <= EXPIRING_SOON_WINDOW_MS -> CouponStatus.EXPIRING_SOON
            else -> CouponStatus.ACTIVE
        }
    }

    private companion object {
        const val EXPIRING_SOON_WINDOW_MS = 24 * 60 * 60 * 1000L
    }
}

fun List<Coupon>.sortedForDisplay(nowMillis: Long): List<Coupon> =
    sortedWith(compareBy<Coupon>({ it.statusAt(nowMillis).ordinal }, { it.expiresAtMillis ?: Long.MAX_VALUE }))
