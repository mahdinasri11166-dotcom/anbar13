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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** فهرست فاکتورها با فیلتر و برگشت سریع */
class SalesListActivity : AppCompatActivity() {
    private lateinit var repo: InventoryRepository
    private lateinit var box: LinearLayout
    private var filter = "ALL" // ALL COMPLETED RETURNED CREDIT

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        repo = InventoryRepository(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFFF1F5F9.toInt())
        }
        root.addView(TextView(this).apply {
            text = "فاکتورهای فروش"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("ALL" to "همه", "COMPLETED" to "موفق", "RETURNED" to "برگشتی", "CREDIT" to "نسیه").forEach { (k, label) ->
            chips.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                textSize = 11f
                setOnClickListener { filter = k; load() }
            })
        }
        root.addView(chips)
        val et = EditText(this).apply {
            hint = "جستجو شماره فاکتور یا مشتری"
            setPadding(24, 20, 24, 20)
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { load(s?.toString().orEmpty()) }
        })
        root.addView(et)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(box)
        })
        setContentView(root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "فاکتورها"
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load(q: String = "") {
        box.removeAllViews()
        var list = repo.getSales()
        list = when (filter) {
            "COMPLETED" -> list.filter { it.status == "COMPLETED" && it.originalSaleId == 0L && it.total > 0 }
            "RETURNED" -> list.filter { it.status == "RETURNED" || it.originalSaleId > 0 }
            "CREDIT" -> list.filter { it.isCredit }
            else -> list
        }
        if (q.isNotBlank()) list = list.filter {
            it.invoiceNumber.contains(q, true) || it.customerName.contains(q, true)
        }
        if (list.isEmpty()) {
            box.addView(TextView(this).apply { text = "فاکتوری نیست"; setPadding(8, 24, 8, 8) })
            return
        }
        list.take(80).forEach { s ->
            val card = MaterialCardView(this).apply {
                radius = 14f; cardElevation = 0f; useCompatPadding = true
                setCardBackgroundColor(Color.WHITE)
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 12, 14, 12)
            }
            col.addView(TextView(this).apply {
                text = "${s.invoiceNumber}  ·  ${s.customerName}"
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            col.addView(TextView(this).apply {
                text = "${FormatUtils.dateTime(s.timestamp)}\n${FormatUtils.money(s.total)} · ${s.paymentMethod} · ${s.status}"
                textSize = 12f
                setTextColor(0xFF475569.toInt())
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(MaterialButton(this).apply {
                text = "مشاهده"
                textSize = 12f
                setOnClickListener {
                    startActivity(Intent(this@SalesListActivity, InvoiceActivity::class.java).putExtra("sale_id", s.id))
                }
            })
            if (s.status == "COMPLETED" && s.originalSaleId == 0L && s.total > 0) {
                row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "برگشت"
                    textSize = 12f
                    setOnClickListener {
                        AlertDialog.Builder(this@SalesListActivity)
                            .setTitle("برگشت ${s.invoiceNumber}؟")
                            .setPositiveButton("بله") { _, _ ->
                                val r = repo.returnSale(s.id)
                                Toast.makeText(this@SalesListActivity, if (r != null) "برگشت شد" else repo.lastError, Toast.LENGTH_LONG).show()
                                load(q)
                            }
                            .setNegativeButton("خیر", null).show()
                    }
                })
            }
            col.addView(row)
            card.addView(col)
            box.addView(card)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
