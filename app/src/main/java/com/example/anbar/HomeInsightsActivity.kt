package com.example.anbar

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

/** مرکز فرمان — خلاصه عملیاتی لحظه‌ای */
class HomeInsightsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        val repo = InventoryRepository(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 24)
            setBackgroundColor(0xFF0F172A.toInt())
        }

        fun kpi(title: String, value: String, sub: String, bg: String) {
            val c = MaterialCardView(this).apply {
                radius = 16f; cardElevation = 0f; useCompatPadding = true
                setCardBackgroundColor(Color.parseColor(bg))
            }
            c.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 16, 18, 16)
                addView(TextView(this@HomeInsightsActivity).apply {
                    text = title; textSize = 12f; setTextColor(0xFFCBD5E1.toInt())
                })
                addView(TextView(this@HomeInsightsActivity).apply {
                    text = value; textSize = 22f; setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
                addView(TextView(this@HomeInsightsActivity).apply {
                    text = sub; textSize = 12f; setTextColor(0xFFE2E8F0.toInt())
                })
            })
            root.addView(c)
        }

        val kpiD = repo.getDailyKpi()
        val (thisM, prevM, growth) = repo.getMonthComparison()
        val deliv = repo.getDeliveries()
        val low = repo.getReorderSuggestions().size
        val debtors = repo.getCustomersWithDebt().size
        val over = repo.customersOverCredit().size

        root.addView(TextView(this).apply {
            text = "مرکز فرمان کالانو"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(4, 4, 4, 12)
        })
        kpi("فروش امروز", FormatUtils.money(kpiD.todaySalesTotal), "${kpiD.todaySalesCount} فاکتور", "#1E3A8A")
        kpi("این ماه / ماه قبل", FormatUtils.money(thisM), "رشد ${String.format("%.1f", growth)}٪ · قبل: ${FormatUtils.money(prevM)}", if (growth >= 0) "#14532D" else "#7F1D1D")
        kpi("پیک فعال", "${deliv.count { it.status != "DELIVERED" && it.status != "CANCELLED" }}", "کل ${deliv.size} سفارش", "#4C1D95")
        kpi("هشدارها", "$low کمبود · $over سقف اعتبار", "$debtors مشتری بدهکار", "#9A3412")

        val logs = try { repo.getActivityLogs(12) } catch (_: Exception) { emptyList() }
        root.addView(TextView(this).apply {
            text = "آخرین فعالیت‌ها"
            textSize = 16f
            setTextColor(0xFFE2E8F0.toInt())
            setPadding(4, 16, 4, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        if (logs.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "هنوز فعالیتی ثبت نشده"
                setTextColor(0xFF94A3B8.toInt())
            })
        } else {
            logs.forEach { log ->
                root.addView(TextView(this).apply {
                    text = "• ${log.action}: ${log.detail}  (${FormatUtils.dateTime(log.timestamp)})"
                    textSize = 12f
                    setTextColor(0xFFCBD5E1.toInt())
                    setPadding(4, 4, 4, 4)
                })
            }
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF0F172A.toInt())
            addView(root)
        })
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "مرکز فرمان"
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
