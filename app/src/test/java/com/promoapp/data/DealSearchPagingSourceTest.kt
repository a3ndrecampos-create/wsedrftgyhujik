package com.promoapp.data

import androidx.paging.PagingSource
import com.promoapp.data.paging.DealSearchPagingSource
import com.promoapp.data.remote.FakeDealApi
import com.promoapp.domain.model.SearchFilters
import com.promoapp.domain.model.SearchSort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DealSearchPagingSourceTest {
    private suspend fun firstPage(filters: SearchFilters): PagingSource.LoadResult.Page<Int, com.promoapp.domain.model.Deal> {
        val source = DealSearchPagingSource(FakeDealApi(), filters)
        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))
        @Suppress("UNCHECKED_CAST")
        return result as PagingSource.LoadResult.Page<Int, com.promoapp.domain.model.Deal>
    }

    @Test
    fun `store filter only returns deals from those stores`() =
        runTest {
            val page = firstPage(SearchFilters(stores = setOf("Amazon")))

            assertTrue(page.data.isNotEmpty())
            assertTrue(page.data.all { it.storeName == "Amazon" })
        }

    @Test
    fun `price range is respected`() =
        runTest {
            val page = firstPage(SearchFilters(minPrice = 100.0, maxPrice = 300.0))

            assertTrue(page.data.isNotEmpty())
            assertTrue(page.data.all { it.price in 100.0..300.0 })
        }

    @Test
    fun `lowest price sort returns ascending prices`() =
        runTest {
            val page = firstPage(SearchFilters(sort = SearchSort.LOWEST_PRICE))

            val prices = page.data.map { it.price }
            assertEquals(prices.sorted(), prices)
        }

    @Test
    fun `query matches titles case-insensitively`() =
        runTest {
            val page = firstPage(SearchFilters(query = "fone"))

            assertTrue(page.data.isNotEmpty())
            assertTrue(page.data.all { it.title.contains("fone", ignoreCase = true) || it.storeName.contains("fone", ignoreCase = true) })
        }

    @Test
    fun `short page ends pagination`() =
        runTest {
            val page = firstPage(SearchFilters(query = "Fone", category = "Áudio", maxPrice = 150.0))

            assertTrue(page.data.size < 20)
            assertNull(page.nextKey)
            assertNull(page.prevKey)
        }
}
