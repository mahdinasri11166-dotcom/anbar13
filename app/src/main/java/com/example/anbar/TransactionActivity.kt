package com.example.anbar

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.anbar.databinding.ActivityTransactionBinding
import java.util.Calendar

class TransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionBinding
    private lateinit var repository: InventoryRepository
    private var isEntry = true
    private var products: List<Product> = emptyList()
    private var warehouses: List<Warehouse> = emptyList()
    private var selectedProduct: Product? = null
    private var selectedWarehouse: Warehouse? = null
    private var selectedVariant: ProductVariant? = null
    private var variants: List<ProductVariant> = emptyList()
    private var expiryTimestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        isEntry = intent.getBooleanExtra("is_entry", true)
        repository = InventoryRepository(this)
        products = repository.getProducts()
        warehouses = repository.getWarehouses()

        binding.tvTitle.text = if (isEntry) getString(R.string.entry) else getString(R.string.exit)
        supportActionBar?.title = binding.tvTitle.text
        binding.btnConfirm.setBackgroundColor(
            getColor(if (isEntry) R.color.success else R.color.error)
        )

        setupProductDropdown()
        setupWarehouseDropdown()

        val preProductId = intent.getLongExtra("product_id", -1L)
        if (preProductId > 0) {
            val idx = products.indexOfFirst { it.id == preProductId }
            if (idx >= 0) {
                selectedProduct = products[idx]
                binding.actvProduct.setText(products[idx].name, false)
                onProductSelected()
            }
        }

        binding.btnConfirm.setOnClickListener { confirm() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.etExpiry.setOnClickListener { showDatePicker() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupProductDropdown() {
        val names = products.map { it.name }
        binding.actvProduct.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        )
        binding.actvProduct.setOnClickListener { binding.actvProduct.showDropDown() }
        binding.actvProduct.setOnItemClickListener { _, _, pos, _ ->
            selectedProduct = products[pos]
            onProductSelected()
        }
    }

    private fun setupWarehouseDropdown() {
        val names = warehouses.map { it.name }
        binding.actvWarehouse.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        )
        binding.actvWarehouse.setOnClickListener { binding.actvWarehouse.showDropDown() }
        binding.actvWarehouse.setOnItemClickListener { _, _, pos, _ ->
            selectedWarehouse = warehouses[pos]
                refreshStockHint()
        }
        if (warehouses.isNotEmpty()) {
            selectedWarehouse = warehouses.first { it.isDefault }
            binding.actvWarehouse.setText(selectedWarehouse!!.name, false)
        }
    }

    private fun onProductSelected() {
        val p = selectedProduct ?: return
        variants = if (p.hasVariants) repository.getVariants(p.id) else emptyList()
        if (p.hasVariants && variants.isNotEmpty()) {
            binding.tilVariant.visibility = View.VISIBLE
            val vNames = variants.map { it.name }
            binding.actvVariant.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, vNames)
            )
            binding.actvVariant.setOnClickListener { binding.actvVariant.showDropDown() }
            binding.actvVariant.setOnItemClickListener { _, _, pos, _ ->
                selectedVariant = variants.getOrNull(pos)
                refreshStockHint()
            }
        } else {
            binding.tilVariant.visibility = View.GONE
            selectedVariant = null
        }

        if (p.trackBatch && isEntry) {
            binding.tilBatch.visibility = View.VISIBLE
            binding.tilExpiry.visibility = View.VISIBLE
        } else {
            binding.tilBatch.visibility = if (p.trackBatch) View.VISIBLE else View.GONE
            binding.tilExpiry.visibility = View.GONE
        }
        if (binding.etAmount.text.isNullOrBlank()) {
            binding.etAmount.setText("1")
        }
        refreshStockHint()
    }

    private fun refreshStockHint() {
        val p = selectedProduct ?: return
        val w = selectedWarehouse ?: repository.getDefaultWarehouse()
        val qty = repository.availableQty(p.id, w.id, selectedVariant?.id)
        val priceHint = if (p.salePrice > 0) " · قیمت فروش: ${FormatUtils.money(p.salePrice)}" else ""
        binding.tvTitle.text = buildString {
            append(if (isEntry) "ورود کالا" else "خروج کالا")
            append("\n")
            append("موجودی فعلی: ${FormatUtils.qty(qty)} ${p.unit}")
            append(priceHint)
        }
        if (!isEntry && qty <= 0) {
            Toast.makeText(this, "این کالا در انبار انتخاب‌شده موجودی ندارد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                expiryTimestamp = cal.timeInMillis
                binding.etExpiry.setText("$y/${m + 1}/$d")
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun confirm() {
        val product = selectedProduct
        val warehouse = selectedWarehouse ?: repository.getDefaultWarehouse()
        if (product == null) {
            Toast.makeText(this, "ابتدا کالا را انتخاب کنید", Toast.LENGTH_SHORT).show()
            binding.actvProduct.requestFocus()
            return
        }
        val amount = binding.etAmount.text?.toString()?.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.etAmount.error = "تعداد باید بیشتر از صفر باشد"
            binding.etAmount.requestFocus()
            return
        }
        val batch = binding.etBatch.text?.toString()?.trim().orEmpty()
        val note = binding.etNote.text?.toString()?.trim().orEmpty()
        val variantId = selectedVariant?.id

        if (!isEntry) {
            val avail = repository.availableQty(product.id, warehouse.id, variantId)
            if (avail + 0.0001 < amount) {
                Toast.makeText(
                    this,
                    "موجودی کافی نیست. موجود: ${FormatUtils.qty(avail)} ${product.unit}",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        val success = if (isEntry) {
            repository.entry(
                product.id, warehouse.id, amount, variantId, batch, expiryTimestamp, note
            )
        } else {
            repository.exit(product.id, warehouse.id, amount, variantId, batch, note)
        }

        if (success) {
            val msg = if (isEntry)
                "ورود ${FormatUtils.qty(amount)} ${product.unit} «${product.name}» ثبت شد"
            else
                "خروج ${FormatUtils.qty(amount)} ${product.unit} «${product.name}» ثبت شد"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "عملیات ناموفق — موجودی یا داده را بررسی کنید", Toast.LENGTH_LONG).show()
        }
    }
}
