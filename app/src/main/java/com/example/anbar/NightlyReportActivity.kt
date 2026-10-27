package com.example.anbar

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

/** گزارش یک‌صفحه‌ای روزانه */
class NightlyReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)

        val repo = InventoryRepository(this)
        val summary = repo.buildNightlySummary()
        val kpi = repo.getDailyKpi()

        val scroll = android.widget.ScrollView(this)
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#F4F6F9"))
        }

        root.addView(TextView(this).apply {
            text = "🌙 گزارش امروز"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#0F172A"))
            setPadding(0, 0, 0, 16)
        })

        fun metricCard(title: String, value: String, color: String): MaterialCardView {
            return MaterialCardView(this).apply {
                radius = 16f
                cardElevation = 2f
                setCardBackgroundColor(android.graphics.Color.WHITE)
                useCompatPadding = true
                addView(android.widget.LinearLayout(this@NightlyReportActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(28, 24, 28, 24)
                    addView(TextView(this@NightlyReportActivity).apply {
                        text = title
                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#64748B"))
                    })
                    addView(TextView(this@NightlyReportActivity).apply {
                        text = value
                        textSize = 20f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.parseColor(color))
                        setPadding(0, 6, 0, 0)
                    })
                })
            }
        }

        root.addView(metricCard("فروش امروز", FormatUtils.money(kpi.todaySalesTotal), "#00A86B"))
        root.addView(metricCard("تعداد فاکتور", kpi.todaySalesCount.toString(), "#0A4D8C"))
        root.addView(metricCard("سود تقریبی", FormatUtils.money(kpi.todayProfit), "#FF6D00"))
        root.addView(metricCard("کمبود موجودی", kpi.lowStockCount.toString(), "#D32F2F"))

        root.addView(MaterialCardView(this).apply {
            radius = 16f
            cardElevation = 2f
            useCompatPadding = true
            setCardBackgroundColor(android.graphics.Color.WHITE)
            addView(TextView(this@NightlyReportActivity).apply {
                text = summary
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#0F172A"))
                setPadding(28, 24, 28, 24)
                setLineSpacing(6f, 1f)
            })
        })

        root.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = "اشتراک گزارش"
            setOnClickListener {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "گزارش شبانه آپ انبار")
                    putExtra(android.content.Intent.EXTRA_TEXT, summary)
                }
                startActivity(android.content.Intent.createChooser(send, "اشتراک گزارش"))
            }
        })

        scroll.addView(root)
        setContentView(scroll)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "گزارش شبانه"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
