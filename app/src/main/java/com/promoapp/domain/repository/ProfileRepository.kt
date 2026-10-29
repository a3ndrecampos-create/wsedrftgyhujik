package com.promoapp.domain.repository

import kotlinx.coroutines.flow.Flow

/** Perfil local do usuário. Quando houver login, este contrato passa a vir da conta. */
interface ProfileRepository {
    val displayName: Flow<String>
    suspend fun setDisplayName(name: String)
}
