package com.example.anbar

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class AdvancedReportsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        val repo = InventoryRepository(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 24)
            setBackgroundColor(0xFFF1F5F9.toInt())
        }

        fun card(title: String, body: String, color: String = "#FFFFFF") {
            val c = MaterialCardView(this).apply {
                radius = 14f; cardElevation = 0f; useCompatPadding = true
                setCardBackgroundColor(Color.parseColor(color))
            }
            c.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                addView(TextView(this@AdvancedReportsActivity).apply {
                    text = title; textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@AdvancedReportsActivity).apply {
                    text = body; textSize = 13f; setPadding(0, 6, 0, 0)
                })
            })
            root.addView(c)
        }

        val (thisM, prevM, growth) = repo.getMonthComparison()
        card(
            "مقایسه ماهانه",
            "این ماه: ${FormatUtils.money(thisM)}\nماه قبل: ${FormatUtils.money(prevM)}\nرشد: ${String.format("%.1f", growth)}٪",
            if (growth >= 0) "#D1FAE5" else "#FEE2E2"
        )

        val profits = repo.getProductProfits(30).take(15)
        card(
            "سود کالایی ۳۰ روز",
            if (profits.isEmpty()) "داده کافی نیست"
            else profits.joinToString("\n") {
                "• ${it.productName}: سود ${FormatUtils.money(it.profit)} (فروش ${FormatUtils.money(it.revenue)})"
            }
        )

        val deliv = repo.getDeliveries()
        card(
            "گزارش پیک",
            "کل: ${deliv.size}\nآماده: ${deliv.count { it.status == "READY" }}\nدر مسیر: ${deliv.count { it.status == "IN_TRANSIT" }}\nتحویل: ${deliv.count { it.status == "DELIVERED" }}\nلغو: ${deliv.count { it.status == "CANCELLED" }}"
        )

        try {
            val vis = repo.getVisitorOrders()
            card(
                "گزارش ویزیتور",
                "کل سفارش: ${vis.size}\nمبلغ: ${FormatUtils.money(vis.sumOf { it.total })}\nباز: ${vis.count { it.status == "OPEN" }}"
            )
        } catch (_: Exception) {}

        val over = repo.customersOverCredit()
        card(
            "نزدیک سقف اعتبار",
            if (over.isEmpty()) "مشکلی نیست"
            else over.joinToString("\n") {
                "• ${it.name}: ${FormatUtils.money(it.creditBalance)} / ${FormatUtils.money(it.creditLimit)}"
            },
            if (over.isEmpty()) "#FFFFFF" else "#FEF3C7"
        )

        val reorder = repo.getReorderSuggestions().take(12)
        card(
            "کمبود موجودی / پیشنهاد خرید",
            if (reorder.isEmpty()) "وضعیت موجودی مناسب است"
            else reorder.joinToString("\n") {
                "• ${it.productName}: ${FormatUtils.qty(it.currentStock)} → +${FormatUtils.qty(it.suggestedQty)}"
            }
        )

        setContentView(ScrollView(this).apply { addView(root) })
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "گزارش‌های پیشرفته"
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
