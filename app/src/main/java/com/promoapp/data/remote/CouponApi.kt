package com.promoapp.data.remote

import retrofit2.http.GET

interface CouponApi {
    @GET("coupons")
    suspend fun getCoupons(): List<CouponDto>
}
