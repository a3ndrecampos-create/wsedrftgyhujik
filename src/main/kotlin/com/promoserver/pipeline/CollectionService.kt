package com.promoserver.pipeline

import com.promoserver.collectors.OfferCollector
import com.promoserver.db.OfferRepository
import com.promoserver.db.RunRepository
import com.promoserver.model.CollectionReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class CollectionService(
    private val collectors: List<OfferCollector>,
    private val normalizer: OfferNormalizer,
    private val repository: OfferRepository,
    private val runs: RunRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val log = LoggerFactory.getLogger(CollectionService::class.java)
    private val mutex = Mutex()

    val collectorNames: List<String> get() = collectors.map { it.name }

    /** Roda todos os coletores (ou só o da loja indicada). Um coletor com erro não derruba os outros. */
    suspend fun runAll(only: String? = null): List<CollectionReport> = mutex.withLock {
        collectors.filter { only == null || it.name.equals(only, ignoreCase = true) }.map { runOne(it) }
    }

    private suspend fun runOne(collector: OfferCollector): CollectionReport {
        val startedAt = clock()
        val report = collect(collector, startedAt)
        runs?.let { history -> withContext(Dispatchers.IO) { history.save(report, startedAt, clock() - startedAt) } }
        return report
    }

    private suspend fun collect(collector: OfferCollector, startedAt: Long): CollectionReport {
        return try {
            val raw = collector.collect()
            val accepted = raw.mapNotNull(normalizer::normalize).distinctBy { it.externalId }
            val result = withContext(Dispatchers.IO) { repository.upsertCollected(accepted, startedAt) }
            // Se o coletor não trouxe nada, não expira: pode ser falha da API, não fim das promoções.
            val expired = if (raw.isNotEmpty()) {
                withContext(Dispatchers.IO) { repository.deactivateUnseen(collector.name, startedAt) }
            } else {
                0
            }
            log.info("{}: {} lidas, {} aceitas, {} novas, {} atualizadas, {} expiradas", collector.name, raw.size, accepted.size, result.inserted, result.updated, expired)
            CollectionReport(collector.name, raw.size, accepted.size, result.inserted, result.updated, expired)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Falha ao coletar {}: {}", collector.name, e.message)
            CollectionReport(collector.name, error = e.message ?: e::class.simpleName)
        }
    }
}
