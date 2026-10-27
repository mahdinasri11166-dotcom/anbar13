package com.example.anbar

import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class SalesReturnActivity : AppCompatActivity() {
    private lateinit var repo: InventoryRepository
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        repo = InventoryRepository(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFF1F5F9.toInt())
        }
        root.addView(TextView(this).apply {
            text = "برگشت از فروش"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "فاکتور را انتخاب کنید تا موجودی برگردد و بدهی نسیه اصلاح شود."
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 4, 0, 12)
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) })
        setContentView(root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "برگشت از فروش"
        load()
    }

    private fun load() {
        list.removeAllViews()
        val sales = repo.getSales().filter {
            it.status == "COMPLETED" && it.originalSaleId == 0L && it.total > 0
        }.take(40)
        if (sales.isEmpty()) {
            list.addView(TextView(this).apply { text = "فاکتوری برای برگشت نیست"; setPadding(8, 20, 8, 8) })
            return
        }
        sales.forEach { s ->
            val card = MaterialCardView(this).apply {
                radius = 14f; cardElevation = 0f; useCompatPadding = true
                setCardBackgroundColor(Color.WHITE)
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
            }
            box.addView(TextView(this).apply {
                text = "${s.invoiceNumber} · ${s.customerName}"
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            box.addView(TextView(this).apply {
                text = "${FormatUtils.dateTime(s.timestamp)}\n${FormatUtils.money(s.total)} · ${if (s.isCredit) "نسیه" else "نقد"}"
                textSize = 12f
                setTextColor(0xFF475569.toInt())
            })
            box.addView(MaterialButton(this).apply {
                text = "ثبت برگشت"
                setOnClickListener { confirmReturn(s) }
            })
            card.addView(box)
            list.addView(card)
        }
    }

    private fun confirmReturn(s: Sale) {
        val et = EditText(this).apply { hint = "دلیل برگشت (اختیاری)"; setPadding(40, 24, 40, 24) }
        AlertDialog.Builder(this)
            .setTitle("برگشت ${s.invoiceNumber}؟")
            .setMessage("موجودی به انبار برمی‌گردد. مبلغ: ${FormatUtils.money(s.total)}")
            .setView(et)
            .setPositiveButton("تأیید برگشت") { _, _ ->
                val ret = repo.returnSale(s.id, et.text?.toString().orEmpty())
                if (ret != null) {
                    Toast.makeText(this, "برگشت ثبت شد: ${ret.invoiceNumber}", Toast.LENGTH_LONG).show()
                    load()
                } else {
                    Toast.makeText(this, repo.lastError.ifBlank { "خطا" }, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
