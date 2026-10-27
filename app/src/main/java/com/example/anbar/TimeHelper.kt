package com.example.anbar

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * ساعت و تاریخ برنامه:
 * - تلاش برای همگام‌سازی با سرور (هدر Date)
 * - در صورت آفلاین: ساعت دستگاه
 * - offset برای یکسان‌سازی نسبی
 */
object TimeHelper {
    private val offsetMs = AtomicLong(0)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun now(): Long = System.currentTimeMillis() + offsetMs.get()

    fun dateTime(): String =
        SimpleDateFormat("yyyy/MM/dd  HH:mm:ss", Locale.US).format(Date(now()))

    fun stamp(): Long = now()

    /** همگام‌سازی پس‌زمینه با شبکه */
    fun syncOnline(context: Context? = null, onDone: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var ok = false
            try {
                val url = URL("https://www.google.com/generate_204")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 4000
                    readTimeout = 4000
                    instanceFollowRedirects = false
                }
                conn.connect()
                val dateHeader = conn.getHeaderField("Date")
                conn.disconnect()
                if (!dateHeader.isNullOrBlank()) {
                    val server = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("GMT") }
                        .parse(dateHeader)?.time
                    if (server != null) {
                        offsetMs.set(server - System.currentTimeMillis())
                        ok = true
                    }
                }
            } catch (e: Exception) {
                Log.d("TimeHelper", "offline or sync fail: ${e.message}")
            }
            onDone?.let { cb -> main.post { cb(ok) } }
        }
    }
}
