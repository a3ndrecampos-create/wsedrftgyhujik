package com.promoapp.domain.model

enum class SearchSort {
    RELEVANCE,
    LOWEST_PRICE,
    BIGGEST_DISCOUNT,
    NEWEST,
}

data class SearchFilters(
    val query: String = "",
    val stores: Set<String> = emptySet(),
    val category: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sort: SearchSort = SearchSort.RELEVANCE,
) {
    /** Quantidade de filtros ativos, sem contar o texto digitado. */
    val activeFilterCount: Int
        get() =
            listOf(
                stores.isNotEmpty(),
                category != null,
                minPrice != null || maxPrice != null,
                sort != SearchSort.RELEVANCE,
            ).count { it }

    /** Só vale a pena consultar a API quando há texto ou algum filtro ativo. */
    val hasCriteria: Boolean
        get() = query.isNotBlank() || activeFilterCount > 0

    /** Corrige faixa de preço invertida (mínimo maior que máximo). */
    fun normalized(): SearchFilters =
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            copy(minPrice = maxPrice, maxPrice = minPrice)
        } else {
            this
        }
}

data class FilterOptions(
    val stores: List<String>,
    val categories: List<String>,
)
