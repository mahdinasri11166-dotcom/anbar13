package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.anbar.databinding.ActivityExportBinding
import java.io.File

class ExportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportBinding
    private lateinit var repository: InventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityExportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.export_title)

        repository = InventoryRepository(this)

        // خلاصه لحظه‌ای گزارش
        val kpi = repository.getDailyKpi()
        val salesN = repository.getSales().count { it.status == "COMPLETED" }
        val deliv = repository.getDeliveries()
        binding.tvExportStatus.text = buildString {
            append("امروز: ${FormatUtils.money(kpi.todaySalesTotal)} فروش")
            append(" · ")
            append("${salesN} فاکتور")
            append(" · ")
            append("${deliv.count { it.status != "DELIVERED" && it.status != "CANCELLED" }} پیک فعال")
            append(" · ")
            append("${repository.getCustomers().size} مشتری")
        }

        binding.btnCsvInventory.setOnClickListener {
            share(ExportHelper.exportInventoryCsv(this, repository), "text/csv")
        }
        binding.btnCsvSales.setOnClickListener {
            share(ExportHelper.exportSalesCsv(this, repository), "text/csv")
        }
        binding.btnCsvCustomers.setOnClickListener {
            share(ExportHelper.exportCustomersCsv(this, repository), "text/csv")
        }
        binding.btnCsvSuppliers.setOnClickListener {
            share(ExportHelper.exportSuppliersCsv(this, repository), "text/csv")
        }
        binding.btnCsvPurchases.setOnClickListener {
            share(ExportHelper.exportPurchasesCsv(this, repository), "text/csv")
        }
        binding.btnCsvAll.setOnClickListener {
            try {
                val files = ExportHelper.exportAllSheets(this, repository)
                // اشتراک چند فایل
                val uris = ArrayList(files.map {
                    FileProvider.getUriForFile(this, "$packageName.provider", it)
                })
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/csv"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_SUBJECT, "گزارش‌های آپ انبار")
                }
                startActivity(Intent.createChooser(intent, "ارسال همه گزارش‌ها"))
                binding.tvExportStatus.text = "${files.size} فایل CSV ساخته شد"
                repository.logActivity("EXPORT", "همه CSV")
            } catch (e: Exception) {
                Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        binding.btnPdfInventory.setOnClickListener {
            share(ExportHelper.exportInventoryPdf(this, repository), "application/pdf")
        }
        binding.btnPdfSales.setOnClickListener {
            share(ExportHelper.exportSalesPdf(this, repository), "application/pdf")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun share(file: File, mime: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "ارسال ${file.name}"))
            binding.tvExportStatus.text = "آماده: ${file.name}"
            repository.logActivity("EXPORT", file.name)
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
