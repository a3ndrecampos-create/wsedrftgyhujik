package com.promoapp.domain.usecase

import com.promoapp.domain.model.SearchFilters
import com.promoapp.domain.repository.DealRepository
import javax.inject.Inject

class SearchDealsUseCase
    @Inject
    constructor(
        private val repository: DealRepository,
    ) {
        operator fun invoke(filters: SearchFilters) = repository.searchDeals(filters.normalized())
    }
