package com.promoapp.data.repository

import com.promoapp.data.remote.CouponApi
import com.promoapp.data.remote.safeApiCall
import com.promoapp.data.remote.toDomain
import com.promoapp.domain.model.Coupon
import com.promoapp.domain.repository.CouponRepository
import javax.inject.Inject

class CouponRepositoryImpl
    @Inject
    constructor(
        private val api: CouponApi,
    ) : CouponRepository {
        override suspend fun getCoupons(): Result<List<Coupon>> =
            safeApiCall {
                api.getCoupons().map { it.toDomain() }
            }
    }
