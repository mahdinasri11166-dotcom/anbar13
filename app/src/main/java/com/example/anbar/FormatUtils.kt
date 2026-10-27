package com.example.anbar

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** فرمت پول چندارزی + تاریخ/ساعت */
object FormatUtils {
    private val us = Locale.US

    /** کد ارز: TOMAN | RIAL | USD | USDT */
    @Volatile
    var currencyCode: String = "TOMAN"

    @Volatile
    var rateUsdToToman: Double = 900_000.0

    @Volatile
    var rateUsdtToToman: Double = 900_000.0

    /** SHAMSI | GREGORIAN */
    @Volatile
    var calendarType: String = "SHAMSI"

    fun applyFromSettings(s: AppSettings) {
        calendarType = if (s.calendarType.uppercase().contains("GREG")) "GREGORIAN" else "SHAMSI"
        currencyCode = s.currency.uppercase(Locale.US).let {
            when {
                it.contains("USDT") || it.contains("تتر") -> "USDT"
                it.contains("USD") || it.contains("دلار") -> "USD"
                it.contains("RIAL") || it.contains("ریال") -> "RIAL"
                else -> "TOMAN"
            }
        }
        rateUsdToToman = s.rateUsdToToman.takeIf { it > 0 } ?: 900_000.0
        rateUsdtToToman = s.rateUsdtToToman.takeIf { it > 0 } ?: 900_000.0
    }

    fun currencyLabel(): String = when (currencyCode) {
        "RIAL" -> "ریال"
        "USD" -> "دلار"
        "USDT" -> "تتر"
        else -> "تومان"
    }

    fun currencySymbol(): String = when (currencyCode) {
        "RIAL" -> "﷼"
        "USD" -> "$"
        "USDT" -> "₮"
        else -> "تومان"
    }

    /**
     * v به واحد پایه (تومان) ذخیره شده؛ برای نمایش تبدیل می‌شود.
     * اگر ارز تومان/ریال باشد: ریال = تومان × ۱۰
     */
    fun money(v: Double): String {
        val display = when (currencyCode) {
            "RIAL" -> v * 10.0
            "USD" -> if (rateUsdToToman > 0) v / rateUsdToToman else v
            "USDT" -> if (rateUsdtToToman > 0) v / rateUsdtToToman else v
            else -> v
        }
        val num = try {
            if (currencyCode == "USD" || currencyCode == "USDT") {
                String.format(us, "%,.2f", display)
            } else {
                NumberFormat.getNumberInstance(us).format(display.toLong())
            }
        } catch (_: Exception) {
            String.format(us, "%,.0f", display)
        }
        return when (currencyCode) {
            "USD" -> "$$num"
            "USDT" -> "$num ₮"
            "RIAL" -> "$num ریال"
            else -> "$num تومان"
        }
    }

    fun qty(v: Double): String {
        return if (kotlin.math.abs(v - v.toLong()) < 0.001) v.toLong().toString()
        else String.format(us, "%.2f", v)
    }

    fun dateTime(ts: Long = System.currentTimeMillis()): String {
        return if (calendarType == "SHAMSI") {
            JalaliHelper.formatDateTime(ts)
        } else {
            SimpleDateFormat("yyyy/MM/dd  HH:mm:ss", us).format(Date(ts))
        }
    }

    fun date(ts: Long = System.currentTimeMillis()): String {
        return if (calendarType == "SHAMSI") {
            JalaliHelper.formatDate(ts)
        } else {
            SimpleDateFormat("yyyy/MM/dd", us).format(Date(ts))
        }
    }

    fun time(ts: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("HH:mm:ss", us).format(Date(ts))

    fun isShamsi(): Boolean = calendarType == "SHAMSI"
}
