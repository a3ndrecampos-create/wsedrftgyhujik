package com.promoapp.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.promoapp.data.remote.DealApi
import com.promoapp.data.remote.toDomain
import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.SearchFilters
import retrofit2.HttpException
import java.io.IOException

/** Busca online (sem cache): cada combinação de filtros vira uma nova fonte de paginação. */
class DealSearchPagingSource(
    private val api: DealApi,
    private val filters: SearchFilters,
) : PagingSource<Int, Deal>() {
    override fun getRefreshKey(state: PagingState<Int, Deal>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { it.prevKey?.plus(1) ?: it.nextKey?.minus(1) }
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Deal> {
        val page = params.key ?: FIRST_PAGE
        return try {
            val items =
                api
                    .searchDeals(
                        query = filters.query.takeIf { it.isNotBlank() },
                        stores = filters.stores.takeIf { it.isNotEmpty() }?.joinToString(","),
                        category = filters.category,
                        minPrice = filters.minPrice,
                        maxPrice = filters.maxPrice,
                        sort = filters.sort.name.lowercase(),
                        page = page,
                        pageSize = params.loadSize,
                    ).map { it.toDomain() }
            LoadResult.Page(
                data = items,
                prevKey = if (page == FIRST_PAGE) null else page - 1,
                nextKey = if (items.size < params.loadSize) null else page + 1,
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
