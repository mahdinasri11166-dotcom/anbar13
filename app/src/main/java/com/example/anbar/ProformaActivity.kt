package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import java.io.File

/** پیش‌فاکتور شیک با نام/آدرس/تلفن فروشگاه */
class ProformaActivity : AppCompatActivity() {

    private lateinit var repo: InventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        repo = InventoryRepository(this)
        val s = repo.getSettings()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFF7F9FC.toInt())
        }
        root.addView(TextView(this).apply {
            text = "📄 پیش‌فاکتور"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "${s.shopName}\n${s.shopAddress}\n${s.shopPhone}"
            textSize = 13f
            setPadding(0, 8, 0, 16)
            setTextColor(0xFF5A6A7A.toInt())
        })

        val etCustomer = EditText(this).apply { hint = "نام مشتری" }
        val etItem = EditText(this).apply { hint = "شرح کالا (دستی)" }
        val etQty = EditText(this).apply {
            hint = "تعداد"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etPrice = EditText(this).apply {
            hint = "قیمت واحد"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etNote = EditText(this).apply { hint = "توضیحات" }
        listOf(etCustomer, etItem, etQty, etPrice, etNote).forEach { root.addView(it) }

        root.addView(MaterialButton(this).apply {
            text = "ساخت و اشتراک PDF پیش‌فاکتور"
            setOnClickListener {
                val name = etCustomer.text?.toString()?.trim().orEmpty().ifEmpty { "مشتری" }
                val item = etItem.text?.toString()?.trim().orEmpty().ifEmpty { "کالا" }
                val qty = etQty.text?.toString()?.toDoubleOrNull() ?: 1.0
                val price = etPrice.text?.toString()?.toDoubleOrNull() ?: 0.0
                val total = qty * price
                try {
                    val file = buildPdf(s, name, item, qty, price, total, etNote.text?.toString().orEmpty())
                    val uri = FileProvider.getUriForFile(this@ProformaActivity, "$packageName.provider", file)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "اشتراک پیش‌فاکتور"))
                } catch (e: Exception) {
                    Toast.makeText(this@ProformaActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "پیش‌فاکتور"
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun buildPdf(
        s: AppSettings, customer: String, item: String,
        qty: Double, price: Double, total: Double, note: String
    ): File {
        return InvoicePdfHelper.build(
            context = this,
            settings = s,
            docTitle = "پیش‌فاکتور",
            invoiceNo = "شماره موقت: PF-${System.currentTimeMillis() % 100000}",
            dateText = FormatUtils.dateTime(),
            customerLine = "مشتری: $customer",
            extraLines = if (note.isNotBlank()) listOf("توضیح: $note") else emptyList(),
            items = listOf(
                InvoicePdfHelper.Line(
                    name = item.ifBlank { "کالا" },
                    qty = FormatUtils.qty(qty),
                    unitPrice = FormatUtils.money(price),
                    total = FormatUtils.money(total)
                )
            ),
            summaryRows = listOf("جمع اقلام" to FormatUtils.money(total)),
            grandTotalLabel = "مبلغ پیش‌فاکتور",
            grandTotalValue = FormatUtils.money(total),
            footer = s.invoiceFooter.ifBlank { "از خرید شما متشکریم" },
            verse = MolanaHelper.randomVerse(this),
            filePrefix = "proforma"
        )
    }
}
