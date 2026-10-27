package com.example.anbar

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.anbar.databinding.ActivityInvoiceBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** فاکتور فروش — نمایش جدولی + PDF حرفه‌ای */
class InvoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInvoiceBinding
    private lateinit var repository: InventoryRepository
    private var sale: Sale? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityInvoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "فاکتور فروش"

        repository = InventoryRepository(this)
        val saleId = intent.getLongExtra("sale_id", -1L)
        sale = repository.getSale(saleId)
        if (sale == null) {
            Toast.makeText(this, "فاکتور پیدا نشد", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        displayInvoice(sale!!)
        binding.btnSharePdf.setOnClickListener { shareAsPdf(sale!!) }
        binding.btnWhatsApp.setOnClickListener { shareWhatsApp(sale!!) }
        binding.btnBluetoothPrint.setOnClickListener { printBluetooth(sale!!) }
        binding.btnSaveLocal.setOnClickListener { saveToDevice(sale!!) }
        binding.btnDone.setOnClickListener { finish() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun displayInvoice(s: Sale) {
        val settings = repository.getSettings()
        binding.tvInvoiceShop.text = buildString {
            append(settings.shopName.ifBlank { "کالانو" })
            if (settings.shopAddress.isNotBlank()) append("\n${settings.shopAddress}")
            if (settings.shopPhone.isNotBlank()) append("\n☎ ${settings.shopPhone}")
        }
        binding.tvInvoiceNumber.text = "شماره ${s.invoiceNumber}"
        binding.tvInvoiceDate.text = FormatUtils.dateTime(s.timestamp)
        binding.tvInvoiceCustomer.text = "مشتری: ${s.customerName.ifBlank { "متفرقه" }}" +
                if (s.isCredit) "  ·  نسیه" else "  ·  نقدی"

        binding.layoutItems.removeAllViews()

        // هدر جدول
        binding.layoutItems.addView(tableHeader())

        s.items.forEachIndexed { idx, item ->
            val name = item.productName +
                    if (item.variantName.isNotBlank()) " (${item.variantName})" else ""
            binding.layoutItems.addView(
                tableRow(
                    name,
                    "${FormatUtils.qty(item.quantity)} ${item.unit}",
                    FormatUtils.money(item.unitPrice),
                    FormatUtils.money(item.lineTotal),
                    idx % 2 == 1
                )
            )
        }

        binding.tvInvoiceSummary.text = buildString {
            append("جمع اقلام:  ${FormatUtils.money(s.subtotal)}\n")
            if (s.discountAmount > 0) append("تخفیف:  ${FormatUtils.money(s.discountAmount)}\n")
            if (s.vatAmount > 0) append("مالیات:  ${FormatUtils.money(s.vatAmount)}\n")
            append("─────────────────\n")
            append("قابل پرداخت:  ${FormatUtils.money(s.total)}")
            if (s.paidAmount > 0 && s.paidAmount < s.total) {
                append("\nپرداخت‌شده:  ${FormatUtils.money(s.paidAmount)}")
                append("\nمانده:  ${FormatUtils.money(s.total - s.paidAmount)}")
            }
            append("\n\n")
            append(settings.invoiceFooter.ifBlank { "از خرید شما متشکریم" })
            append("\n\n« ")
            append(MolanaHelper.randomVerse(this@InvoiceActivity))
            append(" »")
        }
    }

    private fun tableHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#DBEAFE"))
            setPadding(10, 12, 10, 12)
            addView(cell("کالا", 0.40f, true, Color.parseColor("#1E40AF")))
            addView(cell("تعداد", 0.18f, true, Color.parseColor("#1E40AF")))
            addView(cell("قیمت واحد", 0.20f, true, Color.parseColor("#1E40AF")))
            addView(cell("مبلغ", 0.22f, true, Color.parseColor("#1E40AF")))
        }
    }

    private fun tableRow(
        name: String, qty: String, price: String, total: String, stripe: Boolean
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            if (stripe) setBackgroundColor(Color.parseColor("#F8FAFC"))
            setPadding(10, 12, 10, 12)
            addView(cell(name, 0.40f, false, Color.parseColor("#0F172A")))
            addView(cell(qty, 0.18f, false, Color.parseColor("#334155")))
            addView(cell(price, 0.20f, false, Color.parseColor("#334155")))
            addView(cell(total, 0.22f, true, Color.parseColor("#0F172A")))
        }
    }

    private fun cell(text: String, weight: Float, bold: Boolean, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            maxLines = 2
        }
    }

    private fun buildPdf(s: Sale): java.io.File {
        val settings = repository.getSettings()
        val items = s.items.map {
            val n = it.productName + if (it.variantName.isNotBlank()) " (${it.variantName})" else ""
            InvoicePdfHelper.Line(
                name = n,
                qty = "${FormatUtils.qty(it.quantity)} ${it.unit}",
                unitPrice = FormatUtils.money(it.unitPrice),
                total = FormatUtils.money(it.lineTotal)
            )
        }
        val summary = mutableListOf(
            "جمع اقلام" to FormatUtils.money(s.subtotal)
        )
        if (s.discountAmount > 0) summary.add("تخفیف" to FormatUtils.money(s.discountAmount))
        if (s.vatAmount > 0) summary.add("مالیات" to FormatUtils.money(s.vatAmount))
        if (s.paidAmount > 0 && s.paidAmount < s.total) {
            summary.add("پرداخت‌شده" to FormatUtils.money(s.paidAmount))
            summary.add("مانده" to FormatUtils.money(s.total - s.paidAmount))
        }
        return InvoicePdfHelper.build(
            context = this,
            settings = settings,
            docTitle = if (s.isCredit) "فاکتور فروش (نسیه)" else "فاکتور فروش (نقدی)",
            invoiceNo = "شماره: ${s.invoiceNumber}",
            dateText = InvoicePdfHelper.formatDate(s.timestamp),
            customerLine = "مشتری: ${s.customerName.ifBlank { "متفرقه" }}",
            items = items,
            summaryRows = summary,
            grandTotalLabel = "قابل پرداخت",
            grandTotalValue = FormatUtils.money(s.total),
            footer = settings.invoiceFooter.ifBlank { "از خرید شما متشکریم" },
            verse = if (settings.invoiceShowPoetry) MolanaHelper.randomVerse(this) else "",
            filePrefix = "invoice"
        )
    }


    /** ذخیره PDF فاکتور در پوشه Downloads دستگاه */
    private fun saveToDevice(s: Sale) {
        try {
            val file = buildPdf(s)
            val name = "Kalano_Invoice_${s.invoiceNumber}_${System.currentTimeMillis()}.pdf"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Kalano")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("عدم دسترسی به حافظه")
                contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: throw Exception("خطا در نوشتن فایل")
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val kalano = java.io.File(dir, "Kalano").apply { mkdirs() }
                val dest = java.io.File(kalano, name)
                file.copyTo(dest, overwrite = true)
            }
            Toast.makeText(this, "در Downloads/Kalano ذخیره شد", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ذخیره: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareAsPdf(s: Sale) {
        try {
            val file = buildPdf(s)
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "فاکتور ${s.invoiceNumber}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "اشتراک فاکتور PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareWhatsApp(s: Sale) {
        try {
            val file = buildPdf(s)
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val text = buildString {
                append("فاکتور ${s.invoiceNumber}\n")
                append("مبلغ: ${FormatUtils.money(s.total)}\n")
                append(repository.getSettings().shopName)
            }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "ارسال فاکتور"))
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun printBluetooth(s: Sale) {
        // متن ESC/POS برای اپ‌های پرینتر حرارتی
        try {
            val text = EscPosHelper.receiptText(repository.getSettings(), s)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "چاپ حرارتی / متن"))
        } catch (_: Exception) {}

        try {
            val file = buildPdf(s)
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "چاپ فاکتور"))
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
