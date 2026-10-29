package com.promoapp.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.promoapp.MainActivity
import com.promoapp.R
import com.promoapp.domain.model.PriceAlert
import com.promoapp.ui.components.formatBrl

object PriceAlertNotifier {
    private const val CHANNEL_ID = "price_alerts"

    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun notify(
        context: Context,
        alert: PriceAlert,
    ) {
        if (!hasNotificationPermission(context)) return

        val openDeal =
            Intent(Intent.ACTION_VIEW, "promoapp://deal/${alert.dealId}".toUri(), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                alert.dealId.toInt(),
                openDeal,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notif_title))
                .setContentText(context.getString(R.string.notif_text, alert.title, formatBrl(alert.lastKnownPrice)))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context).notify(alert.dealId.toInt(), notification)
    }

    internal fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
