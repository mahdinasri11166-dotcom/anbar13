package com.example.anbar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.anbar.databinding.ActivityDashboardBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var repository: InventoryRepository
    private var topPeriodDays = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "داشبورد مدیریتی"

        repository = InventoryRepository(this)
        setupTopPeriodChips()
        loadAll()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupTopPeriodChips() {
        val periods = listOf(
            7 to "هفته",
            30 to "ماه",
            90 to "۳ ماه",
            180 to "۶ ماه",
            365 to "یک سال"
        )
        // چیپ‌ها داخل خود layoutTopProducts تا وابستگی به parent نداشته باشیم
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        periods.forEach { (days, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = days == topPeriodDays
                setOnClickListener {
                    topPeriodDays = days
                    for (i in 0 until chipRow.childCount) {
                        (chipRow.getChildAt(i) as? Chip)?.isChecked = false
                    }
                    isChecked = true
                    loadTopProducts()
                }
            }
            chipRow.addView(chip)
        }
        binding.layoutTopProducts.addView(chipRow, 0)
    }

    private fun loadAll() {
        val kpi = repository.getDailyKpi()
        binding.tvKpiTodaySales.text = format(kpi.todaySalesTotal)
        binding.tvKpiTodayProfit.text = format(kpi.todayProfit)
        binding.tvKpiMonth.text = format(kpi.monthSalesTotal)
        binding.tvKpiLowStock.text = "${kpi.lowStockCount} / ${kpi.expiringCount}"
        binding.tvKpiReceivable.text = format(kpi.totalReceivables)
        binding.tvKpiPayable.text = format(kpi.totalPayables)

        binding.tvKpiTodayProfit.setTextColor(
            if (kpi.todayProfit >= 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        )

        loadTopProducts()

        val (thisM, prevM, growth) = repository.getMonthComparison()
        try {
            binding.tvPL.text = "این ماه: ${format(thisM)} | ماه قبل: ${format(prevM)} | رشد: ${String.format("%.1f", growth)}٪\n\n" + binding.tvPL.text
        } catch (_: Exception) {}
        val (thisM, prevM, growth) = repository.getMonthComparison()
        val pl = repository.getPLReport()
        binding.tvPL.text = buildString {
            append("این ماه: ${format(thisM)}  |  ماه قبل: ${format(prevM)}\n")
            append("رشد: ${String.format("%.1f", growth)}٪\n")
            append("─────────────────\n")
            append("درآمد فروش: ${format(pl.revenue)}\n")
            append("بهای تمام‌شده: ${format(pl.cogs)}\n")
            append("سود ناخالص: ${format(pl.grossProfit)}\n")
            append("─────────────────\n")
            append("سود خالص تقریبی: ${format(pl.netProfit)}")
        }
        binding.tvPL.setTextColor(
            if (pl.netProfit >= 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        )

        val forecast = repository.getStockForecast().take(15)
        binding.layoutForecast.removeAllViews()
        if (forecast.isEmpty()) {
            addText(binding.layoutForecast, "داده کافی برای پیش‌بینی نیست")
        } else {
            forecast.forEach { f ->
                val daysText = when {
                    f.daysUntilEmpty < 0 -> "بدون فروش اخیر"
                    f.daysUntilEmpty == 0 -> "⚠ در حال اتمام"
                    else -> "≈ ${f.daysUntilEmpty} روز تا اتمام"
                }
                addBarRow(
                    binding.layoutForecast,
                    f.productName,
                    "موجودی: ${format(f.currentStock)} ${f.unit}  ·  $daysText",
                    when {
                        f.daysUntilEmpty < 0 -> 15
                        f.daysUntilEmpty == 0 -> 95
                        f.daysUntilEmpty < 7 -> 80
                        f.daysUntilEmpty < 30 -> 50
                        else -> 25
                    },
                    accent = if (f.daysUntilEmpty in 0..7) "#EF4444" else "#2563EB"
                )
            }
        }
    }

    private fun loadTopProducts() {
        val tops = repository.getTopProducts(limit = 12, days = topPeriodDays)
        val chipRow = if (binding.layoutTopProducts.childCount > 0)
            binding.layoutTopProducts.getChildAt(0) else null
        binding.layoutTopProducts.removeAllViews()
        if (chipRow != null) binding.layoutTopProducts.addView(chipRow)
        val periodLabel = when (topPeriodDays) {
            7 -> "هفته"
            30 -> "ماه"
            90 -> "۳ ماه"
            180 -> "۶ ماه"
            else -> "یک سال"
        }
        addText(binding.layoutTopProducts, "🏆 پرفروش‌ترین‌های $periodLabel")
        val maxRev = tops.maxOfOrNull { it.revenue } ?: 1.0
        if (tops.isEmpty()) {
            addText(binding.layoutTopProducts, "در این بازه فروشی ثبت نشده")
        } else {
            tops.forEachIndexed { i, t ->
                val pct = (t.revenue / maxRev * 100).toInt().coerceIn(4, 100)
                addBarRow(
                    binding.layoutTopProducts,
                    "${i + 1}. ${t.productName}",
                    "${format(t.quantitySold)} عدد  ·  ${format(t.revenue)} ${FormatUtils.currencyLabel()}",
                    pct,
                    accent = listOf("#2563EB", "#7C3AED", "#DB2777", "#059669", "#D97706")[i % 5]
                )
            }
        }
    }

    private fun addText(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(8, 8, 8, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
    }

    private fun addBarRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        percent: Int,
        accent: String = "#2563EB"
    ) {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        })
        row.addView(TextView(this).apply {
            text = subtitle
            textSize = 11.5f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 4, 0, 6)
        })
        // نوار پیشرفت رنگی سفارشی
        val track = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (10 * density).toInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(Color.parseColor("#E2E8F0"))
            }
        }
        val barHost = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (10 * density).toInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(Color.parseColor("#E2E8F0"))
            }
        }
        barHost.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, percent.toFloat())
            background = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(Color.parseColor(accent))
            }
        })
        if (percent < 100) {
            barHost.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100 - percent).toFloat())
            })
        }
        row.addView(barHost)

        val card = MaterialCardView(this).apply {
            radius = 14 * density
            cardElevation = 0f
            strokeWidth = (1 * density).toInt().coerceAtLeast(1)
            strokeColor = Color.parseColor("#E2E8F0")
            setCardBackgroundColor(Color.WHITE)
            useCompatPadding = true
            addView(row)
        }
        parent.addView(card)
    }

    private fun format(v: Double): String = FormatUtils.money(v)
}
