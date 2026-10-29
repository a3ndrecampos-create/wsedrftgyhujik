package com.promoapp.domain.usecase

import com.promoapp.domain.repository.DealRepository
import javax.inject.Inject

class GetDealDetailUseCase @Inject constructor(
    private val repository: DealRepository,
) {
    suspend operator fun invoke(id: Long) = repository.getDealDetail(id)
}
