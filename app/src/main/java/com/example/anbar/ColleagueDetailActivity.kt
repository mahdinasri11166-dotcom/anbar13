package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.anbar.databinding.ActivityColleagueDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ColleagueDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityColleagueDetailBinding
    private lateinit var repository: InventoryRepository
    private var colleagueId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityColleagueDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.colleagues)

        repository = InventoryRepository(this)
        colleagueId = intent.getLongExtra("colleague_id", -1L)

        binding.btnHandover.setOnClickListener {
            startActivity(Intent(this, HandoverActivity::class.java).apply {
                putExtra("colleague_id", colleagueId)
                putExtra("is_return", false)
            })
        }
        binding.btnReturn.setOnClickListener {
            startActivity(Intent(this, HandoverActivity::class.java).apply {
                putExtra("colleague_id", colleagueId)
                putExtra("is_return", true)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load() {
        val c = repository.getColleague(colleagueId) ?: run { finish(); return }
        binding.tvColName.text = c.name
        binding.tvColPhone.text = c.phone.ifBlank { "بدون شماره" }

        val balances = repository.getColleagueBalances(colleagueId)
        binding.layoutBalances.removeAllViews()
        if (balances.isEmpty()) {
            binding.tvNoBalance.visibility = View.VISIBLE
            binding.tvNoBalance.text = "مانده‌ای ندارد"
        } else {
            binding.tvNoBalance.visibility = View.GONE
            balances.forEach { b ->
                val tv = TextView(this).apply {
                    text = "${b.productName}: ${fmt(b.quantity)} ${b.unit}"
                    textSize = 14f
                    setPadding(12, 10, 12, 10)
                    setBackgroundColor(0xFFFFFFFF.toInt())
                }
                val card = com.google.android.material.card.MaterialCardView(this).apply {
                    radius = 10f
                    cardElevation = 2f
                    useCompatPadding = true
                    addView(tv)
                }
                binding.layoutBalances.addView(card)
            }
        }

        val history = repository.getColleagueHandovers(colleagueId)
        binding.layoutHistory.removeAllViews()
        val df = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
        history.forEach { h ->
            val typeFa = if (h.type == "HANDOVER") "تحویل" else "بازتحویل"
            val items = h.items.joinToString("، ") { "${it.productName}×${fmt(it.quantity)}" }
            val tv = TextView(this).apply {
                text = "${h.receiptNumber}  ·  $typeFa  ·  ${df.format(Date(h.timestamp))}\n$items"
                textSize = 12f
                setPadding(12, 10, 12, 10)
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 10f
                cardElevation = 2f
                useCompatPadding = true
                addView(tv)
            }
            binding.layoutHistory.addView(card)
        }
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
}
