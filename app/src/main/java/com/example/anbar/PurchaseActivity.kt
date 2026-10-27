package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.anbar.databinding.ActivityPurchaseBinding

class PurchaseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseBinding
    private lateinit var repository: InventoryRepository
    private var suppliers: List<Supplier> = emptyList()
    private var warehouses: List<Warehouse> = emptyList()
    private var products: List<Product> = emptyList()
    private var selectedSupplier: Supplier? = null
    private var selectedWarehouse: Warehouse? = null
    private var selectedProduct: Product? = null
    private val items = mutableListOf<PurchaseItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "فاکتور خرید"

        repository = InventoryRepository(this)
        loadLists()

        binding.actvSupplier.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suppliers.map { it.name })
        )
        binding.actvSupplier.setOnClickListener { binding.actvSupplier.showDropDown() }
        binding.actvSupplier.setOnItemClickListener { _, _, pos, _ -> selectedSupplier = suppliers[pos] }

        binding.actvWarehouse.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, warehouses.map { it.name })
        )
        binding.actvWarehouse.setOnClickListener { binding.actvWarehouse.showDropDown() }
        binding.actvWarehouse.setOnItemClickListener { _, _, pos, _ -> selectedWarehouse = warehouses[pos] }
        if (warehouses.isNotEmpty()) {
            selectedWarehouse = warehouses.firstOrNull { it.isDefault } ?: warehouses.first()
            binding.actvWarehouse.setText(selectedWarehouse!!.name, false)
        }

        setupProductDropdown()

        binding.btnAddItem.setOnClickListener { addItem() }
        binding.btnPurchaseCash.setOnClickListener { complete(isCredit = false) }
        binding.btnPurchaseCredit.setOnClickListener { complete(isCredit = true) }

        binding.actvProduct.hint = "کالا (اختیاری) — کالای جدید در انتهای لیست"
        binding.actvProduct.setOnLongClickListener {
            promptNewProduct()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        val prevId = selectedProduct?.id
        loadLists()
        setupProductDropdown()
        if (prevId != null) {
            selectedProduct = products.find { it.id == prevId }
            selectedProduct?.let { binding.actvProduct.setText(it.name, false) }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadLists() {
        suppliers = repository.getSuppliers()
        warehouses = repository.getWarehouses()
        products = repository.getProducts()
    }

    private fun setupProductDropdown() {
        val labels = products.map { it.name } + "＋ کالای جدید…"
        binding.actvProduct.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        )
        binding.actvProduct.setOnClickListener { binding.actvProduct.showDropDown() }
        binding.actvProduct.setOnItemClickListener { _, _, pos, _ ->
            if (pos >= products.size) {
                promptNewProduct()
                binding.actvProduct.setText("", false)
                selectedProduct = null
            } else {
                selectedProduct = products[pos]
            }
        }
    }

    private fun promptNewProduct() {
        AlertDialog.Builder(this)
            .setTitle("کالای جدید")
            .setMessage("صفحه ثبت کالا باز شود؟ پس از ذخیره می‌توانید آن را به فاکتور اضافه کنید.")
            .setPositiveButton("ثبت کالا") { _, _ ->
                startActivity(Intent(this, AddProductActivity::class.java))
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun addItem() {
        val p = selectedProduct
        if (p == null) {
            Toast.makeText(
                this,
                "کالا را از لیست انتخاب کنید یا «کالای جدید» بسازید",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val qty = binding.etQty.text?.toString()?.toDoubleOrNull() ?: 1.0
        if (qty <= 0) {
            binding.etQty.error = "تعداد نامعتبر"
            return
        }
        val cost = binding.etCost.text?.toString()?.toDoubleOrNull() ?: 0.0
        items.add(
            PurchaseItem(
                productId = p.id,
                productName = p.name,
                quantity = qty,
                unitCost = cost,
                lineTotal = qty * cost
            )
        )
        binding.etQty.setText("1")
        binding.etCost.text?.clear()
        binding.actvProduct.setText("", false)
        selectedProduct = null
        refreshItems()
    }

    private fun refreshItems() {
        binding.recyclerPurchaseItems.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    setPadding(24, 16, 24, 16)
                    textSize = 14f
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val it = items[position]
                (holder.itemView as TextView).text =
                    "${it.productName}  ${format(it.quantity)} × ${format(it.unitCost)} = ${format(it.lineTotal)}"
            }

            override fun getItemCount() = items.size
        }
        binding.tvPurchaseTotal.text = "جمع: ${format(items.sumOf { it.lineTotal })}"
    }

    private fun complete(isCredit: Boolean) {
        val supplier = selectedSupplier
        val warehouse = selectedWarehouse
        if (supplier == null || warehouse == null) {
            Toast.makeText(this, "تامین‌کننده و انبار را انتخاب کنید", Toast.LENGTH_SHORT).show()
            return
        }
        // افزودن کالا اجباری نیست
        val total = items.sumOf { it.lineTotal }
        val paid = if (isCredit) 0.0 else total
        val result = repository.completePurchase(
            supplierId = supplier.id,
            warehouseId = warehouse.id,
            items = items.toList(),
            paidAmount = paid,
            isCredit = isCredit
        )
        if (result != null) {
            try {
                WebhookSender.notifyPurchase(this, result)
            } catch (_: Exception) {
            }
            val msg = if (items.isEmpty())
                "فاکتور خرید (بدون کالا) ثبت شد"
            else
                "فاکتور خرید ثبت شد — موجودی افزایش یافت"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            finish()
        } else {
            Toast.makeText(this, "خطا در ثبت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun format(v: Double): String =
        if (v == v.toLong().toDouble()) String.format("%,d", v.toLong())
        else String.format("%,.0f", v)
}
