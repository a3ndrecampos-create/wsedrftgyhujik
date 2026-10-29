package com.promoapp.domain.repository

import androidx.paging.PagingData
import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.DealDetail
import com.promoapp.domain.model.DealDraft
import com.promoapp.domain.model.DealTab
import com.promoapp.domain.model.FilterOptions
import com.promoapp.domain.model.SearchFilters
import kotlinx.coroutines.flow.Flow

interface DealRepository {
    fun getDeals(tab: DealTab): Flow<PagingData<Deal>>
    fun searchDeals(filters: SearchFilters): Flow<PagingData<Deal>>
    suspend fun getFilterOptions(): Result<FilterOptions>
    suspend fun getDealDetail(id: Long): Result<DealDetail>
    suspend fun postDeal(draft: DealDraft): Result<Deal>
    /** Ofertas em alta para escolher a oferta do dia (usado em segundo plano, sem cache). */
    suspend fun getDailyCandidates(): Result<List<Deal>>
    suspend fun getMyDeals(): Result<List<Deal>>
    fun observeFavoriteIds(): Flow<List<Long>>
    suspend fun setFavorite(dealId: Long, favorite: Boolean)
}
