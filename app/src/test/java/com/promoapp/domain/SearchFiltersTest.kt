package com.promoapp.domain

import com.promoapp.domain.model.SearchFilters
import com.promoapp.domain.model.SearchSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFiltersTest {
    @Test
    fun `default filters have no criteria`() {
        val filters = SearchFilters()

        assertEquals(0, filters.activeFilterCount)
        assertFalse(filters.hasCriteria)
    }

    @Test
    fun `query alone is a criteria but not an active filter`() {
        val filters = SearchFilters(query = "fone")

        assertEquals(0, filters.activeFilterCount)
        assertTrue(filters.hasCriteria)
    }

    @Test
    fun `blank query is not a criteria`() {
        assertFalse(SearchFilters(query = "   ").hasCriteria)
    }

    @Test
    fun `each filter group counts once`() {
        val filters =
            SearchFilters(
                stores = setOf("Amazon", "Magalu"),
                category = "Áudio",
                minPrice = 10.0,
                maxPrice = 100.0,
                sort = SearchSort.LOWEST_PRICE,
            )

        assertEquals(4, filters.activeFilterCount)
    }

    @Test
    fun `inverted price range is swapped`() {
        val result = SearchFilters(minPrice = 500.0, maxPrice = 100.0).normalized()

        assertEquals(100.0, result.minPrice!!, 0.001)
        assertEquals(500.0, result.maxPrice!!, 0.001)
    }

    @Test
    fun `valid price range is left untouched`() {
        val filters = SearchFilters(minPrice = 10.0, maxPrice = 20.0)

        assertSame(filters, filters.normalized())
    }
}
