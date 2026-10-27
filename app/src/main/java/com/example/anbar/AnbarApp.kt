package com.example.anbar

import android.app.Application
import android.util.Log

class AnbarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            LocaleHelper.syncFromPrefs(this)
        } catch (e: Exception) {
            Log.e("AnbarApp", "locale/theme init failed", e)
        }
        try {
            // ارز و فرمت پول
            InventoryRepository(this).getSettings()
            TimeHelper.syncOnline(this)
        } catch (e: Exception) {
            Log.e("AnbarApp", "currency/time init failed", e)
        }
        try {
            NotificationHelper.ensureChannels(this)
            StockAlertWorker.schedule(this)
            NightlyReportWorker.schedule(this)
            AutoBackupWorker.schedule(this)
        } catch (e: Exception) {
            Log.e("AnbarApp", "workers schedule failed", e)
        }
    }
}
