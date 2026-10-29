package com.promoapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.promoapp.notifications.DailyDealNotifier
import com.promoapp.notifications.PriceAlertNotifier
import com.promoapp.notifications.PriceAlertScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PromoApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var priceAlertScheduler: PriceAlertScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        PriceAlertNotifier.createChannel(this)
        DailyDealNotifier.createChannel(this)
        priceAlertScheduler.schedule()
    }
}
