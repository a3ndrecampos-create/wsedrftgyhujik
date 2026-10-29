package com.promoserver.pipeline

import com.promoserver.db.OfferRepository
import com.promoserver.db.RunRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** Limpeza periódica: ofertas expiradas há muito tempo e histórico de coletas antigo. */
class RetentionService(
    private val offers: OfferRepository,
    private val runs: RunRepository,
    private val retentionDays: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val log = LoggerFactory.getLogger(RetentionService::class.java)

    suspend fun cleanup(): Int = withContext(Dispatchers.IO) {
        val now = clock()
        val offersRemoved = offers.deleteInactiveOlderThan(now - retentionDays * DAY_MS)
        val runsRemoved = runs.deleteOlderThan(now - RUN_HISTORY_DAYS * DAY_MS)
        if (offersRemoved + runsRemoved > 0) log.info("Limpeza: {} ofertas e {} registros de coleta removidos", offersRemoved, runsRemoved)
        offersRemoved
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val RUN_HISTORY_DAYS = 30L
    }
}
