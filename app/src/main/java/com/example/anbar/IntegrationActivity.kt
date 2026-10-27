package com.example.anbar

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.anbar.databinding.ActivityIntegrationBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

class IntegrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntegrationBinding
    private lateinit var repository: InventoryRepository
    private val rateEditors = mutableMapOf<String, EditText>()
    private var qrBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityIntegrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.integration_title)

        repository = InventoryRepository(this)
        loadWebhook()
        loadRates()
        generateQr()

        binding.btnSaveIntegration.setOnClickListener { save() }
        binding.btnShareQr.setOnClickListener { shareCatalog() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadWebhook() {
        val c = repository.getWebhookConfig()
        binding.switchWebhook.isChecked = c.enabled
        binding.etWebhookUrl.setText(c.url)
        binding.etWebhookSecret.setText(c.secret)
        binding.cbWhSale.isChecked = c.onSale
        binding.cbWhPurchase.isChecked = c.onPurchase
    }

    private fun loadRates() {
        binding.layoutRates.removeAllViews()
        rateEditors.clear()
        repository.getCurrencyRates().forEach { rate ->
            if (rate.code == "IRR") return@forEach
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            row.addView(TextView(this).apply {
                text = "${rate.name} (${rate.code})"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 14f
            })
            val et = EditText(this).apply {
                setText(rate.rateToBase.toLong().toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                hint = "نرخ به تومان"
            }
            rateEditors[rate.code] = et
            row.addView(et)
            binding.layoutRates.addView(row)
        }
    }

    private fun generateQr() {
        try {
            val content = repository.catalogText().take(2000) // محدودیت ظرفیت QR
            val size = 512
            val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            qrBitmap = bmp
            binding.imgQr.setImageBitmap(bmp)
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ساخت QR: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun save() {
        val config = WebhookConfig(
            enabled = binding.switchWebhook.isChecked,
            url = binding.etWebhookUrl.text?.toString()?.trim().orEmpty(),
            secret = binding.etWebhookSecret.text?.toString()?.trim().orEmpty(),
            onSale = binding.cbWhSale.isChecked,
            onPurchase = binding.cbWhPurchase.isChecked
        )
        repository.saveWebhookConfig(config)

        val rates = repository.getCurrencyRates().map { r ->
            if (r.code == "IRR") r
            else {
                val v = rateEditors[r.code]?.text?.toString()?.toDoubleOrNull() ?: r.rateToBase
                r.copy(rateToBase = v)
            }
        }
        repository.saveCurrencyRates(rates)
        repository.logActivity("INTEGRATION", "تنظیمات ذخیره شد")
        Toast.makeText(this, getString(R.string.saved_ok), Toast.LENGTH_SHORT).show()
    }

    private fun shareCatalog() {
        try {
            val bmp = qrBitmap ?: return
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: cacheDir
            val file = File(dir, "catalog_qr.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val text = repository.catalogText()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text.take(500))
                putExtra(Intent.EXTRA_SUBJECT, "کاتالوگ ${repository.getSettings().shopName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "اشتراک کاتالوگ"))
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
