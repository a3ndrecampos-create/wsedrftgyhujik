package com.promoapp.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.promoapp.domain.DailyDealPicker
import com.promoapp.domain.repository.DealRepository
import com.promoapp.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Uma vez por dia, escolhe a melhor oferta ainda não avisada e notifica. */
@HiltWorker
class DailyDealWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: DealRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.dailyDealsEnabled.first()) return Result.success()
        val candidates = repository.getDailyCandidates().getOrElse { return Result.retry() }
        val deal = DailyDealPicker.pick(candidates, settings.lastNotifiedDealId.first()) ?: return Result.success()
        DailyDealNotifier.notify(applicationContext, deal)
        settings.setLastNotifiedDealId(deal.id)
        return Result.success()
    }
}
