package com.promoapp.domain.repository

import com.promoapp.domain.model.Coupon

interface CouponRepository {
    suspend fun getCoupons(): Result<List<Coupon>>
}
