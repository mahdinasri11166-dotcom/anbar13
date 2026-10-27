package com.example.anbar

import java.util.Calendar
import java.util.TimeZone

/** تبدیل تاریخ میلادی ↔ شمسی (الگوریتم استاندارد) */
object JalaliHelper {

    data class JDate(val year: Int, val month: Int, val day: Int)

    fun fromMillis(ts: Long): JDate {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = ts }
        return toJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    fun formatDate(ts: Long): String {
        val j = fromMillis(ts)
        return String.format("%04d/%02d/%02d", j.year, j.month, j.day)
    }

    fun formatDateTime(ts: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        val j = fromMillis(ts)
        return String.format(
            "%04d/%02d/%02d  %02d:%02d",
            j.year, j.month, j.day,
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )
    }

    fun toJalali(gy: Int, gm: Int, gd: Int): JDate {
        val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy2 = gy
        if (gy2 <= 1600) {
            gy2 -= 621
        } else {
            gy2 -= 1600
        }
        val days = if (gm > 2) {
            365 * gy2 + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) - 80 + gd + g_d_m[gm - 1] - 1
        } else {
            365 * gy2 + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) - 80 + gd + g_d_m[gm - 1]
        }
        // algorithm simplified well-known conversion
        return gregorianToJalali(gy, gm, gd)
    }

    /** الگوریتم jalaali-js / معروف */
    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JDate {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + (days % 31)
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + ((days - 186) % 30)
        }
        return JDate(jy, jm, jd)
    }
}
