package com.example.anbar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * بکاپ خودکار روزانه در پس‌زمینه
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = InventoryRepository(applicationContext)
            if (!repo.isAutoBackupEnabled()) return Result.success()

            val json = repo.exportBackupJson()
            val dir = applicationContext.getExternalFilesDir("backups")
                ?: applicationContext.filesDir
            if (!dir.exists()) dir.mkdirs()

            // نگه داشتن فقط ۷ بکاپ آخر
            dir.listFiles()?.sortedByDescending { it.lastModified() }
                ?.drop(6)?.forEach { it.delete() }

            val file = File(dir, "auto_backup_${System.currentTimeMillis()}.json")
            FileOutputStream(file).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            repo.setLastAutoBackupPath(file.absolutePath)
            repo.logActivity("AUTO_BACKUP", file.name)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "anbar_daily_backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
