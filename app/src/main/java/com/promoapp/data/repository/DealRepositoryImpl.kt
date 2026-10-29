package com.promoapp.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.promoapp.data.local.AppDatabase
import com.promoapp.data.local.FavoriteEntity
import com.promoapp.data.local.toDomain
import com.promoapp.data.paging.DealRemoteMediator
import com.promoapp.data.paging.DealSearchPagingSource
import com.promoapp.data.remote.DealApi
import com.promoapp.data.remote.safeApiCall
import com.promoapp.data.remote.toDomain
import com.promoapp.data.remote.toRequest
import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.DealDetail
import com.promoapp.domain.model.DealDraft
import com.promoapp.domain.model.DealTab
import com.promoapp.domain.model.FilterOptions
import com.promoapp.domain.model.PriceHistory
import com.promoapp.domain.model.SearchFilters
import com.promoapp.domain.repository.DealRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DealRepositoryImpl
    @Inject
    constructor(
        private val api: DealApi,
        private val database: AppDatabase,
    ) : DealRepository {
        @OptIn(ExperimentalPagingApi::class)
        override fun getDeals(tab: DealTab): Flow<PagingData<Deal>> =
            Pager(
                config =
                    PagingConfig(
                        pageSize = PAGE_SIZE,
                        initialLoadSize = PAGE_SIZE,
                        enablePlaceholders = false,
                    ),
                remoteMediator = DealRemoteMediator(tab, api, database),
                pagingSourceFactory = { database.cachedDealDao().pagingSource(tab.name) },
            ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

        override fun searchDeals(filters: SearchFilters): Flow<PagingData<Deal>> =
            Pager(
                config =
                    PagingConfig(
                        pageSize = PAGE_SIZE,
                        initialLoadSize = PAGE_SIZE,
                        enablePlaceholders = false,
                    ),
                pagingSourceFactory = { DealSearchPagingSource(api, filters) },
            ).flow

        override suspend fun getFilterOptions(): Result<FilterOptions> = safeApiCall { api.getFilterOptions().toDomain() }

        override suspend fun getDealDetail(id: Long): Result<DealDetail> =
            safeApiCall {
                coroutineScope {
                    val deal = async { api.getDeal(id).toDomain() }
                    val history = async { api.getPriceHistory(id).map { it.toDomain() } }
                    DealDetail(deal = deal.await(), history = PriceHistory(history.await()))
                }
            }

        override suspend fun postDeal(draft: DealDraft): Result<Deal> =
            safeApiCall { api.postDeal(draft.toRequest()).toDomain() }
                .onSuccess { database.cachedDealDao().markAllStale() }

        override suspend fun getDailyCandidates(): Result<List<Deal>> =
            safeApiCall { api.getDeals("trending", page = 1, pageSize = DAILY_CANDIDATES).map { it.toDomain() } }

        override suspend fun getMyDeals(): Result<List<Deal>> = safeApiCall { api.getMyDeals().map { it.toDomain() } }

        override fun observeFavoriteIds(): Flow<List<Long>> = database.favoriteDao().observeIds()

        override suspend fun setFavorite(
            dealId: Long,
            favorite: Boolean,
        ) {
            val dao = database.favoriteDao()
            if (favorite) dao.insert(FavoriteEntity(dealId)) else dao.delete(dealId)
        }

        private companion object {
            const val PAGE_SIZE = 20
            const val DAILY_CANDIDATES = 20
        }
    }
