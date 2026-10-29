package com.promoapp.domain.usecase

import com.promoapp.domain.repository.CouponRepository
import javax.inject.Inject

class GetCouponsUseCase
    @Inject
    constructor(
        private val repository: CouponRepository,
    ) {
        suspend operator fun invoke() = repository.getCoupons()
    }
