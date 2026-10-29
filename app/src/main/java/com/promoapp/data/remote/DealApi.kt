package com.promoapp.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DealApi {
    @GET("deals")
    suspend fun getDeals(
        @Query("tab") tab: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): List<DealDto>

    @GET("deals/search")
    suspend fun searchDeals(
        @Query("q") query: String?,
        @Query("stores") stores: String?,
        @Query("category") category: String?,
        @Query("min_price") minPrice: Double?,
        @Query("max_price") maxPrice: Double?,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): List<DealDto>

    @GET("filters")
    suspend fun getFilterOptions(): FilterOptionsDto

    @POST("deals")
    suspend fun postDeal(
        @Body request: NewDealRequest,
    ): DealDto

    @GET("deals/mine")
    suspend fun getMyDeals(): List<DealDto>

    @GET("deals/{id}")
    suspend fun getDeal(
        @Path("id") id: Long,
    ): DealDto

    @GET("deals/{id}/price-history")
    suspend fun getPriceHistory(
        @Path("id") id: Long,
    ): List<PricePointDto>
}
