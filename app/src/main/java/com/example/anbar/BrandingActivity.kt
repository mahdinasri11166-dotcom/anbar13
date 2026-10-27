package com.example.anbar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.anbar.databinding.ActivityBrandingBinding

class BrandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrandingBinding
    private lateinit var repository: InventoryRepository
    private var selectedColor = "#1976D2"

    private val colorOptions = listOf(
        "#1976D2", // آبی
        "#388E3C", // سبز
        "#D32F2F", // قرمز
        "#F57C00", // نارنجی
        "#7B1FA2", // بنفش
        "#00838F", // فیروزه‌ای
        "#5D4037", // قهوه‌ای
        "#455A64"  // خاکستری
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityBrandingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.branding_title)

        repository = InventoryRepository(this)
        val s = repository.getSettings()
        selectedColor = s.primaryColor
        binding.etBrandName.setText(s.shopName)
        binding.etInvoiceFooter.setText(s.invoiceFooter)

        val shortcuts = s.shortcuts.split(",").map { it.trim() }.toSet()
        binding.cbPos.isChecked = "POS" in shortcuts
        binding.cbCustomers.isChecked = "CUSTOMERS" in shortcuts
        binding.cbDashboard.isChecked = "DASHBOARD" in shortcuts
        binding.cbSuppliers.isChecked = "SUPPLIERS" in shortcuts
        binding.cbScan.isChecked = "SCAN" in shortcuts
        binding.cbEntry.isChecked = "ENTRY" in shortcuts

        setupColorPicker()
        binding.btnSaveBranding.setOnClickListener { save() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupColorPicker() {
        binding.layoutColors.removeAllViews()
        colorOptions.forEach { hex ->
            val size = (48 * resources.displayMetrics.density).toInt()
            val margin = (6 * resources.displayMetrics.density).toInt()
            val view = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, margin, margin, margin)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                    if (hex == selectedColor) {
                        setStroke((3 * resources.displayMetrics.density).toInt(), Color.BLACK)
                    }
                }
                setOnClickListener {
                    selectedColor = hex
                    setupColorPicker()
                }
            }
            binding.layoutColors.addView(view)
        }
    }

    private fun save() {
        val name = binding.etBrandName.text?.toString()?.trim().orEmpty().ifEmpty { "فروشگاه من" }
        val footer = binding.etInvoiceFooter.text?.toString()?.trim().orEmpty()
            .ifEmpty { "از خرید شما سپاسگزاریم" }

        val sc = mutableListOf<String>()
        if (binding.cbPos.isChecked) sc.add("POS")
        if (binding.cbCustomers.isChecked) sc.add("CUSTOMERS")
        if (binding.cbDashboard.isChecked) sc.add("DASHBOARD")
        if (binding.cbSuppliers.isChecked) sc.add("SUPPLIERS")
        if (binding.cbScan.isChecked) sc.add("SCAN")
        if (binding.cbEntry.isChecked) sc.add("ENTRY")
        val shortcuts = sc.take(4).joinToString(",")

        val current = repository.getSettings()
        repository.saveSettings(
            current.copy(
                shopName = name,
                primaryColor = selectedColor,
                invoiceFooter = footer,
                shortcuts = shortcuts
            )
        )
        repository.logActivity("BRANDING", "برندینگ به‌روز شد")
        Toast.makeText(this, "ذخیره شد. برای اعمال کامل رنگ، اپ را یک‌بار ببندید و باز کنید.", Toast.LENGTH_LONG).show()
        finish()
    }
}
