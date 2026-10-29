package com.promoapp.domain.usecase

import com.promoapp.domain.repository.DealRepository
import javax.inject.Inject

class GetFilterOptionsUseCase @Inject constructor(
    private val repository: DealRepository,
) {
    suspend operator fun invoke() = repository.getFilterOptions()
}
