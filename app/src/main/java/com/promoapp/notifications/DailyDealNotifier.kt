package com.promoapp.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.promoapp.MainActivity
import com.promoapp.R
import com.promoapp.domain.model.Deal
import com.promoapp.ui.components.formatBrl

object DailyDealNotifier {
    private const val CHANNEL_ID = "daily_deals"
    private const val NOTIFICATION_ID = 7_001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.daily_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun notify(context: Context, deal: Deal) {
        if (!PriceAlertNotifier.hasNotificationPermission(context)) return

        val openDeal = Intent(Intent.ACTION_VIEW, "promoapp://deal/${deal.id}".toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openDeal,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.daily_notif_title))
            .setContentText(context.getString(R.string.daily_notif_text, deal.title, formatBrl(deal.price), deal.discountPercent ?: 0))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
