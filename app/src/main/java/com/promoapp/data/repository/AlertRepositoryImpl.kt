package com.promoapp.data.repository

import com.promoapp.data.local.AlertDao
import com.promoapp.data.local.toDomain
import com.promoapp.data.local.toEntity
import com.promoapp.data.remote.DealApi
import com.promoapp.data.remote.safeApiCall
import com.promoapp.domain.model.Deal
import com.promoapp.domain.model.PriceAlert
import com.promoapp.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AlertRepositoryImpl @Inject constructor(
    private val api: DealApi,
    private val dao: AlertDao,
) : AlertRepository {

    override fun observeAlerts(): Flow<List<PriceAlert>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeAlert(dealId: Long): Flow<PriceAlert?> = dao.observe(dealId).map { it?.toDomain() }

    override suspend fun createAlert(deal: Deal, targetPrice: Double) {
        val alert = PriceAlert(
            dealId = deal.id,
            title = deal.title,
            imageUrl = deal.imageUrl,
            storeName = deal.storeName,
            url = deal.url,
            targetPrice = targetPrice,
            priceWhenCreated = deal.price,
            lastKnownPrice = deal.price,
            createdAtMillis = System.currentTimeMillis(),
            triggeredAtMillis = null,
        )
        dao.upsert(alert.toEntity())
    }

    override suspend fun deleteAlert(dealId: Long) = dao.delete(dealId)

    override suspend fun checkAlerts(): List<PriceAlert> {
        val now = System.currentTimeMillis()
        val triggered = mutableListOf<PriceAlert>()
        for (alert in dao.getActive().map { it.toDomain() }) {
            val price = safeApiCall { api.getDeal(alert.dealId).price }.getOrNull() ?: continue
            val updated = alert.evaluate(price, now)
            if (updated != alert) dao.upsert(updated.toEntity())
            if (updated.isTriggered) triggered += updated
        }
        return triggered
    }
}
