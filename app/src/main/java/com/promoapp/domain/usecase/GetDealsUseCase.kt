package com.promoapp.domain.usecase

import com.promoapp.domain.model.DealTab
import com.promoapp.domain.repository.DealRepository
import javax.inject.Inject

class GetDealsUseCase @Inject constructor(
    private val repository: DealRepository,
) {
    operator fun invoke(tab: DealTab) = repository.getDeals(tab)
}
