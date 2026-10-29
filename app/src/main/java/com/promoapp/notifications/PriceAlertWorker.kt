package com.promoapp.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.promoapp.domain.repository.AlertRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Roda em segundo plano: confere os preços dos alertas ativos e notifica os que chegaram ao alvo. */
@HiltWorker
class PriceAlertWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val repository: AlertRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            repository.checkAlerts().forEach { PriceAlertNotifier.notify(applicationContext, it) }
            return Result.success()
        }
    }
