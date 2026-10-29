package com.promoapp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.promoapp.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ProfileRepository {

    override val displayName: Flow<String> = dataStore.data.map { it[DISPLAY_NAME].orEmpty() }

    override suspend fun setDisplayName(name: String) {
        dataStore.edit { it[DISPLAY_NAME] = name.trim() }
    }

    private companion object {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
    }
}
