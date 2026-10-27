package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.anbar.databinding.ActivityAddProductBinding

class AddProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductBinding
    private lateinit var repository: InventoryRepository
    private val units = listOf("عدد", "کیلوگرم", "متر", "بسته", "لیتر", "جعبه")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddProductBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try {
            binding.etBarcode.setOnLongClickListener {
                binding.etBarcode.setText(InventoryRepository(this).generateInternalBarcode())
                true
            }
        } catch (_: Exception) {}
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.add_product)

        repository = InventoryRepository(this)
        intent.getStringExtra("prefill_barcode")?.let { code ->
            if (code.isNotBlank()) binding.etBarcode.setText(code)
        }
        binding.spinnerUnit.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units)

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun save() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.etName.error = "نام کالا الزامی است"
            binding.etName.requestFocus()
            return
        }
        val code = binding.etCode.text?.toString()?.trim().orEmpty()
            .ifEmpty { "K-${System.currentTimeMillis() % 100000}" }
        val barcode = binding.etBarcode.text?.toString()?.trim().orEmpty()
        val unit = units.getOrElse(binding.spinnerUnit.selectedItemPosition) { "عدد" }
        val hasVariants = binding.cbHasVariants.isChecked
        val trackBatch = binding.cbTrackBatch.isChecked

        val purchase = binding.etPurchasePrice.text?.toString()?.toDoubleOrNull() ?: 0.0
        val sale = binding.etSalePrice.text?.toString()?.toDoubleOrNull() ?: 0.0
        if (sale > 0 && purchase > sale) {
            Toast.makeText(this, "توجه: قیمت فروش کمتر از خرید است", Toast.LENGTH_SHORT).show()
        }
        val category = binding.etCategory.text?.toString()?.trim().orEmpty().ifEmpty { "عمومی" }
        val product = repository.addProduct(
            name, code, barcode, unit, hasVariants, trackBatch, purchase, sale, category
        )
        AlertDialog.Builder(this)
            .setTitle("کالا ثبت شد")
            .setMessage("«${product.name}» ذخیره شد.\nآیا همین حالا ورود موجودی ثبت شود؟")
            .setPositiveButton("ورود موجودی") { _, _ ->
                startActivity(Intent(this, TransactionActivity::class.java).apply {
                    putExtra("is_entry", true)
                    putExtra("product_id", product.id)
                })
                finish()
            }
            .setNegativeButton("بعداً") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
