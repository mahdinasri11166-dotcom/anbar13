package com.example.anbar

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** خروج خودکار پس از بی‌فعالیتی */
object SessionTimeout {
    private val handler = Handler(Looper.getMainLooper())
    private var minutes = 0
    private var activity: Activity? = null
    private val logoutRunnable = Runnable {
        val a = activity ?: return@Runnable
        try {
            a.startActivity(Intent(a, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            a.finishAffinity()
        } catch (_: Exception) {}
    }

    fun configure(minutes: Int) {
        this.minutes = minutes
    }

    fun touch(act: Activity) {
        activity = act
        handler.removeCallbacks(logoutRunnable)
        if (minutes > 0) {
            handler.postDelayed(logoutRunnable, minutes * 60_000L)
        }
    }

    fun clear() {
        handler.removeCallbacks(logoutRunnable)
        activity = null
    }
}
