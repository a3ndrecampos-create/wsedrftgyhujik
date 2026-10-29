package com.promoapp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.promoapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override val dailyDealsEnabled: Flow<Boolean> = dataStore.data.map { it[DAILY_DEALS] ?: false }

        override val lastNotifiedDealId: Flow<Long?> = dataStore.data.map { it[LAST_NOTIFIED] }

        override suspend fun setDailyDealsEnabled(enabled: Boolean) {
            dataStore.edit { it[DAILY_DEALS] = enabled }
        }

        override suspend fun setLastNotifiedDealId(dealId: Long) {
            dataStore.edit { it[LAST_NOTIFIED] = dealId }
        }

        private companion object {
            val DAILY_DEALS = booleanPreferencesKey("daily_deals_enabled")
            val LAST_NOTIFIED = longPreferencesKey("last_notified_deal_id")
        }
    }
