package com.promoserver.pipeline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

class CollectionScheduler(
    private val service: CollectionService,
    private val intervalHours: Long,
    private val runOnStart: Boolean,
    private val afterRun: suspend () -> Unit = {},
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        if (runOnStart) runCycle()
        while (isActive) {
            delay(intervalHours.hours)
            runCycle()
        }
    }

    private suspend fun runCycle() {
        service.runAll()
        try {
            afterRun()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(CollectionScheduler::class.java).warn("Falha na limpeza: {}", e.message)
        }
    }
}
