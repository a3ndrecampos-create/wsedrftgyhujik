package com.promoapp.domain.repository

import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.PriceAlert
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
    fun observeAlerts(): Flow<List<PriceAlert>>

    fun observeAlert(dealId: Long): Flow<PriceAlert?>

    suspend fun createAlert(
        deal: Deal,
        targetPrice: Double,
    )

    suspend fun deleteAlert(dealId: Long)

    /** Consulta o preço atual dos alertas ativos e devolve só os que acabaram de ser atingidos. */
    suspend fun checkAlerts(): List<PriceAlert>
}
