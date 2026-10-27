package com.example.anbar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL_ALERTS = "anbar_alerts"
    const val CHANNEL_REPORTS = "anbar_reports"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "هشدار موجودی و انقضا",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "کمبود کالا و نزدیک انقضا" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REPORTS,
                "گزارش روزانه",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "خلاصه فروش و سود روز" }
        )
    }

    fun show(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        body: String,
        target: Class<*> = MainActivity::class.java
    ) {
        ensureChannels(context)
        val pi = PendingIntent.getActivity(
            context,
            id,
            Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notif)
        } catch (_: SecurityException) {
            // مجوز نوتیفیکیشن داده نشده
        }
    }
}
