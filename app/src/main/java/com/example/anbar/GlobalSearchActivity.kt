package com.example.anbar

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class GlobalSearchActivity : AppCompatActivity() {
    private lateinit var repo: InventoryRepository
    private lateinit var results: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        repo = InventoryRepository(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFF1F5F9.toInt())
        }
        val et = EditText(this).apply {
            hint = "جستجو: کالا، مشتری، فاکتور، پیک…"
            setPadding(28, 28, 28, 28)
            requestFocus()
        }
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(et)
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(results)
        })
        setContentView(root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "جستجوی سراسری"

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { search(s?.toString().orEmpty()) }
        })
        val pre = intent.getStringExtra("q")
        if (!pre.isNullOrBlank()) {
            et.setText(pre)
            search(pre)
        }
    }

    private fun search(q: String) {
        results.removeAllViews()
        val hits = repo.globalSearch(q)
        if (hits.isEmpty()) {
            results.addView(TextView(this).apply {
                text = if (q.isBlank()) "عبارت را وارد کنید" else "نتیجه‌ای یافت نشد"
                setPadding(8, 24, 8, 8)
                setTextColor(0xFF64748B.toInt())
            })
            return
        }
        hits.forEach { h ->
            val card = MaterialCardView(this).apply {
                radius = 14f; cardElevation = 0f; useCompatPadding = true
                setCardBackgroundColor(Color.WHITE)
                setOnClickListener { openHit(h) }
            }
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                addView(TextView(this@GlobalSearchActivity).apply {
                    text = h.title
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@GlobalSearchActivity).apply {
                    text = h.subtitle
                    textSize = 12f
                    setTextColor(0xFF64748B.toInt())
                })
            })
            results.addView(card)
        }
    }

    private fun openHit(h: SearchHit) {
        when (h.type) {
            "PRODUCT" -> startActivity(Intent(this, AddProductActivity::class.java))
            "CUSTOMER" -> startActivity(Intent(this, CustomerDetailActivity::class.java).putExtra("customer_id", h.id))
            "SALE" -> startActivity(Intent(this, InvoiceActivity::class.java).putExtra("sale_id", h.id))
            "DELIVERY" -> startActivity(Intent(this, DeliveryActivity::class.java))
            "VISITOR" -> startActivity(Intent(this, VisitorOrderActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
