package com.example.anbar

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.anbar.databinding.ActivityCommercialBinding
import com.google.android.material.card.MaterialCardView

/**
 * هاب نسخه تجاری ۳.۰ — دسترسی به همه ماژول‌های پیشرفته
 */
class CommercialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommercialBinding
    private lateinit var repository: InventoryRepository

    private data class Item(val title: String, val emoji: String, val mode: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCommercialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.financial_tools)

        repository = InventoryRepository(this)
        setupGrid()
    }

    override fun onResume() {
        super.onResume()
        loadSuggestions()
        val net = repository.getNetProfitWithExpenses(30)
        binding.tvNetProfit.text = getString(R.string.net_profit_30, FormatUtils.money(net))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupGrid() {
        val items = listOf(
            Item(getString(R.string.mod_cash_shift), "💰", "shift"),
            Item(getString(R.string.mod_stocktake), "📋", "stocktake"),
            Item(getString(R.string.mod_checks), "🏦", "checks"),
            Item(getString(R.string.mod_expenses), "💸", "expenses"),
            Item(getString(R.string.mod_proforma), "📄", "proforma"),
            Item(getString(R.string.mod_visitor), "🚗", "visit"),
            Item(getString(R.string.mod_prices), "🏷️", "prices"),
            Item(getString(R.string.mod_bundles), "🎁", "bundles")
        )
        binding.gridCommercial.removeAllViews()
        val d = resources.displayMetrics.density
        items.forEach { item ->
            val card = MaterialCardView(this).apply {
                radius = 14 * d
                cardElevation = 2 * d
                setCardBackgroundColor(Color.WHITE)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                }
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding((14 * d).toInt(), (18 * d).toInt(), (14 * d).toInt(), (18 * d).toInt())
            }
            box.addView(TextView(this).apply {
                text = item.emoji
                textSize = 26f
                gravity = Gravity.CENTER
            })
            box.addView(TextView(this).apply {
                text = item.title
                textSize = 13f
                setTextColor(Color.parseColor("#1A1A2E"))
                gravity = Gravity.CENTER
                setPadding(0, (6 * d).toInt(), 0, 0)
            })
            card.addView(box)
            card.setOnClickListener {
                startActivity(Intent(this, CommercialDetailActivity::class.java).putExtra("mode", item.mode))
            }
            binding.gridCommercial.addView(card)
        }
    }

    private fun loadSuggestions() {
        binding.layoutSuggestions.removeAllViews()
        val list = repository.getPurchaseSuggestions()
        if (list.isEmpty()) {
            binding.tvNoSuggest.visibility = View.VISIBLE
            return
        }
        binding.tvNoSuggest.visibility = View.GONE
        list.take(8).forEach { s ->
            val tv = TextView(this).apply {
                text = "⚠ ${s.productName}\nموجودی ${FormatUtils.qty(s.currentStock)} ${s.unit} · حدود ${s.daysLeft} روز\nپیشنهاد سفارش: ${FormatUtils.qty(s.suggestedQty)}"
                textSize = 13f
                setPadding(14, 12, 14, 12)
                setTextColor(Color.parseColor("#E65100"))
                setBackgroundColor(Color.WHITE)
            }
            val card = MaterialCardView(this).apply {
                radius = 10f
                cardElevation = 1f
                useCompatPadding = true
                addView(tv)
            }
            binding.layoutSuggestions.addView(card)
        }
    }
}
