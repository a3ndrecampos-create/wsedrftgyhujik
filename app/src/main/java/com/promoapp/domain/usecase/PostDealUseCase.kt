package com.promoapp.domain.usecase

import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.DealDraft
import com.promoapp.domain.model.InvalidDraftException
import com.promoapp.domain.repository.DealRepository
import javax.inject.Inject

class PostDealUseCase @Inject constructor(
    private val repository: DealRepository,
) {
    /** Valida de novo antes de enviar, mesmo que a tela já tenha validado. */
    suspend operator fun invoke(draft: DealDraft): Result<Deal> {
        val errors = draft.validate()
        return if (errors.isEmpty()) repository.postDeal(draft) else Result.failure(InvalidDraftException(errors))
    }
}
