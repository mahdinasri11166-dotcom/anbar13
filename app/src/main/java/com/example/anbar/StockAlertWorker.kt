package com.example.anbar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** هشدار روزانه: کمبود، انقضا، سقف اعتبار */
class StockAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = InventoryRepository(applicationContext)
            val stockMap = repo.getAllStock().groupBy { it.productId }.mapValues { it.value.sumOf { s -> s.quantity } }
            val low = repo.getProducts().mapNotNull { p ->
                val q = stockMap[p.id] ?: 0.0
                val min = if (p.minStock > 0) p.minStock else 5.0
                if (q <= min) p.name to q else null
            }
            val exp = repo.getExpiringItems(14)
            val over = repo.customersOverCredit()
            val debt = repo.getCustomersWithDebt().filter { it.creditBalance > 0 }
            val parts = mutableListOf<String>()
            if (low.isNotEmpty()) {
                parts.add("${low.size} کمبود: " + low.take(3).joinToString { it.first })
            }
            if (exp.isNotEmpty()) {
                parts.add("${exp.size} انقضا: " + exp.take(3).joinToString { it.productName })
            }
            if (over.isNotEmpty()) {
                parts.add("${over.size} نزدیک سقف اعتبار")
            }
            if (debt.isNotEmpty()) {
                parts.add("${debt.size} مشتری بدهکار · ${FormatUtils.money(debt.sumOf { it.creditBalance })}")
            }
            if (parts.isNotEmpty()) {
                NotificationHelper.show(
                    applicationContext,
                    NotificationHelper.CHANNEL_ALERTS,
                    1001,
                    "هشدار کالانو",
                    parts.joinToString("\n")
                )
            }
            // گزارش شبانه کوتاه
            val k = repo.getDailyKpi()
            if (k.todaySalesTotal > 0) {
                NotificationHelper.show(
                    applicationContext,
                    NotificationHelper.CHANNEL_REPORTS,
                    1002,
                    "گزارش امروز",
                    "فروش ${FormatUtils.money(k.todaySalesTotal)} · ${k.todaySalesCount} فاکتور"
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK = "anbar_stock_alerts"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<StockAlertWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }
    }
}
