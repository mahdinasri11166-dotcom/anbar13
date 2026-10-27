package com.example.anbar

import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * شمارش فیزیکی انبار: موجودی سیستم vs شمارش واقعی → تعدیل
 */
class StockTakeActivity : AppCompatActivity() {
    private lateinit var repo: InventoryRepository
    private lateinit var box: LinearLayout
    private var stockSnap: List<StockItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        repo = InventoryRepository(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFFF8FAFC.toInt())
        }
        root.addView(TextView(this).apply {
            text = "شمارش موجودی"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "عدد واقعی را وارد کنید. در پایان «اعمال اختلاف» موجودی را تعدیل می‌کند."
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 4, 0, 12)
        })
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(box)
        })
        root.addView(MaterialButton(this).apply {
            text = "اعمال اختلاف‌ها"
            setOnClickListener { applyDiffs() }
        })
        setContentView(root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "شمارش انبار"
        load()
    }

    private fun load() {
        box.removeAllViews()
        stockSnap = repo.getAllStock().filter { it.quantity >= 0 }
        val products = repo.getProducts().associateBy { it.id }
        val warehouses = repo.getWarehouses().associateBy { it.id }
        if (stockSnap.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "موجودی ثبت‌شده‌ای نیست — ابتدا ورود کالا بزنید"
                setPadding(8, 20, 8, 8)
            })
            return
        }
        stockSnap.forEach { s ->
            val p = products[s.productId] ?: return@forEach
            val w = warehouses[s.warehouseId]
            val card = MaterialCardView(this).apply {
                radius = 12f; cardElevation = 0f; useCompatPadding = true
                setCardBackgroundColor(Color.WHITE)
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 10, 12, 10)
            }
            col.addView(TextView(this).apply {
                text = p.name
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            col.addView(TextView(this).apply {
                text = "سیستم: ${FormatUtils.qty(s.quantity)} ${p.unit} · ${w?.name ?: ""}"
                textSize = 12f
                setTextColor(0xFF64748B.toInt())
            })
            val et = EditText(this).apply {
                hint = "شمارش واقعی"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(if (s.quantity == s.quantity.toLong().toDouble()) s.quantity.toLong().toString() else s.quantity.toString())
                setPadding(16, 16, 16, 16)
                tag = s.id // stock id
            }
            col.addView(et)
            card.addView(col)
            box.addView(card)
        }
    }

    private fun applyDiffs() {
        var n = 0
        val byId = stockSnap.associateBy { it.id }
        for (i in 0 until box.childCount) {
            val card = box.getChildAt(i) as? MaterialCardView ?: continue
            val col = card.getChildAt(0) as? LinearLayout ?: continue
            val et = col.getChildAt(2) as? EditText ?: continue
            val stockId = et.tag as? Long ?: continue
            val s = byId[stockId] ?: continue
            val counted = et.text?.toString()?.toDoubleOrNull() ?: continue
            val diff = counted - s.quantity
            if (kotlin.math.abs(diff) < 0.0001) continue
            if (diff > 0) {
                repo.entry(s.productId, s.warehouseId, diff, s.variantId, s.batchNumber, s.expiryDate, "تعدیل شمارش +")
            } else {
                repo.exit(s.productId, s.warehouseId, -diff, s.variantId, s.batchNumber, "تعدیل شمارش -")
            }
            n++
        }
        repo.logActivity("STOCK_TAKE", "adjustments=$n")
        Toast.makeText(this, "$n قلم تعدیل شد", Toast.LENGTH_LONG).show()
        load()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
