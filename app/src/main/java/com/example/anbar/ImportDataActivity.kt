package com.example.anbar

import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ImportDataActivity : AppCompatActivity() {
    private lateinit var repo: InventoryRepository
    private var mode = "products" // products | customers

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            val lines = text.lines().filter { it.isNotBlank() }
            val n = if (mode == "products") repo.importProductsCsv(lines) else repo.importCustomersCsv(lines)
            Toast.makeText(this, "$n ردیف وارد شد", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        repo = InventoryRepository(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFFF8FAFC.toInt())
        }
        root.addView(TextView(this).apply {
            text = "ورود از اکسل / CSV"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "کالا: name,code,barcode,unit,purchase,sale,category,min,max\nمشتری: name,phone,address,creditLimit"
            textSize = 12f
            setPadding(0, 8, 0, 16)
            setTextColor(0xFF64748B.toInt())
        })
        root.addView(MaterialButton(this).apply {
            text = "ورود کالاها از CSV"
            setOnClickListener { mode = "products"; pickFile.launch("*/*") }
        })
        root.addView(MaterialButton(this).apply {
            text = "ورود مشتریان از CSV"
            setOnClickListener { mode = "customers"; pickFile.launch("*/*") }
        })
        root.addView(MaterialButton(this).apply {
            text = "پیشنهاد خرید (کم‌موجودی)"
            setOnClickListener {
                val s = repo.getReorderSuggestions()
                val msg = if (s.isEmpty()) "همه کالاها بالای حداقل موجودی هستند"
                else s.take(20).joinToString("\n") {
                    "• ${it.productName}: موجود ${FormatUtils.qty(it.currentStock)} → پیشنهاد ${FormatUtils.qty(it.suggestedQty)}"
                }
                androidx.appcompat.app.AlertDialog.Builder(this@ImportDataActivity)
                    .setTitle("پیشنهاد سفارش خرید")
                    .setMessage(msg)
                    .setPositiveButton("باشه", null)
                    .show()
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "ورود داده / پیشنهاد خرید"
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
