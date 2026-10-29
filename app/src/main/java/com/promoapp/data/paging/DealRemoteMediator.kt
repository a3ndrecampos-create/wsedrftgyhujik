package com.promoapp.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.promoapp.data.local.AppDatabase
import com.promoapp.data.local.CachedDealEntity
import com.promoapp.data.local.RemoteKeyEntity
import com.promoapp.data.local.toEntity
import com.promoapp.data.remote.DealApi
import com.promoapp.domain.model.DealTab
import retrofit2.HttpException
import java.io.IOException

/**
 * Faz a rede alimentar o banco. A UI lê sempre do Room (fonte única da verdade),
 * então o feed continua disponível offline.
 */
@OptIn(ExperimentalPagingApi::class)
class DealRemoteMediator(
    private val tab: DealTab,
    private val api: DealApi,
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : RemoteMediator<Int, CachedDealEntity>() {
    private val tabKey = tab.name
    private val dealDao = database.cachedDealDao()
    private val keyDao = database.remoteKeyDao()

    override suspend fun initialize(): InitializeAction {
        val lastCachedAt = dealDao.lastCachedAt(tabKey) ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        val isFresh = now() - lastCachedAt < CACHE_TIMEOUT_MS
        return if (isFresh) InitializeAction.SKIP_INITIAL_REFRESH else InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, CachedDealEntity>,
    ): MediatorResult {
        val page =
            when (loadType) {
                LoadType.REFRESH -> FIRST_PAGE
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val key = keyDao.get(tabKey) ?: return MediatorResult.Success(endOfPaginationReached = false)
                    key.nextPage ?: return MediatorResult.Success(endOfPaginationReached = true)
                }
            }

        return try {
            val pageSize = state.config.pageSize
            val items = api.getDeals(tabKey.lowercase(), page, pageSize)
            val endReached = items.size < pageSize

            database.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    dealDao.clear(tabKey)
                    keyDao.clear(tabKey)
                }
                val startPosition = if (loadType == LoadType.REFRESH) 0 else dealDao.lastPosition(tabKey) + 1
                val cachedAt = now()
                dealDao.insertAll(items.mapIndexed { index, dto -> dto.toEntity(tabKey, startPosition + index, cachedAt) })
                keyDao.insert(RemoteKeyEntity(tab = tabKey, nextPage = if (endReached) null else page + 1))
            }
            MediatorResult.Success(endOfPaginationReached = endReached)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val CACHE_TIMEOUT_MS = 15 * 60 * 1000L
    }
}
