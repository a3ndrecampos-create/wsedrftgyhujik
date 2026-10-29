package com.promoapp.domain

import com.promoapp.domain.model.Deal

/** Escolhe a oferta do dia: o maior desconto que ainda não foi avisado. */
object DailyDealPicker {
    const val MIN_DISCOUNT_PERCENT = 15

    fun pick(
        candidates: List<Deal>,
        lastNotifiedId: Long?,
        minDiscountPercent: Int = MIN_DISCOUNT_PERCENT,
    ): Deal? =
        candidates
            .filter { it.id != lastNotifiedId && (it.discountPercent ?: 0) >= minDiscountPercent }
            .sortedWith(compareByDescending<Deal> { it.discountPercent ?: 0 }.thenByDescending { it.postedAtMillis })
            .firstOrNull()
}
