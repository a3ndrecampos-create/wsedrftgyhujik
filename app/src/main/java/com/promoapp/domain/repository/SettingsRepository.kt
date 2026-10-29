package com.promoapp.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val dailyDealsEnabled: Flow<Boolean>
    val lastNotifiedDealId: Flow<Long?>
    suspend fun setDailyDealsEnabled(enabled: Boolean)
    suspend fun setLastNotifiedDealId(dealId: Long)
}
