package com.example.anbar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** اعلان گزارش شبانه فروش */
class NightlyReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val summary = InventoryRepository(applicationContext).buildNightlySummary()
            NotificationHelper.show(
                applicationContext,
                NotificationHelper.CHANNEL_REPORTS,
                2001,
                "🌙 گزارش امروز",
                summary,
                NightlyReportActivity::class.java
            )
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK = "anbar_nightly_report"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<NightlyReportWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }
}
