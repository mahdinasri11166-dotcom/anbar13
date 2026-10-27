package com.example.anbar

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.anbar.databinding.ActivityTransferBinding

class TransferActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransferBinding
    private lateinit var repository: InventoryRepository
    private var products: List<Product> = emptyList()
    private var warehouses: List<Warehouse> = emptyList()
    private var selectedProduct: Product? = null
    private var fromWarehouse: Warehouse? = null
    private var toWarehouse: Warehouse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.transfer)

        repository = InventoryRepository(this)
        products = repository.getProducts()
        warehouses = repository.getWarehouses()

        val pNames = products.map { it.name }
        binding.actvProduct.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, pNames)
        )
        binding.actvProduct.setOnClickListener { binding.actvProduct.showDropDown() }
        binding.actvProduct.setOnItemClickListener { _, _, pos, _ ->
            selectedProduct = products[pos]
        }

        val wNames = warehouses.map { it.name }
        val wAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, wNames)
        binding.actvFrom.setAdapter(wAdapter)
        binding.actvTo.setAdapter(wAdapter)
        binding.actvFrom.setOnClickListener { binding.actvFrom.showDropDown() }
        binding.actvTo.setOnClickListener { binding.actvTo.showDropDown() }
        binding.actvFrom.setOnItemClickListener { _, _, pos, _ -> fromWarehouse = warehouses[pos] }
        binding.actvTo.setOnItemClickListener { _, _, pos, _ -> toWarehouse = warehouses[pos] }

        val preId = intent.getLongExtra("product_id", -1L)
        if (preId > 0) {
            val idx = products.indexOfFirst { it.id == preId }
            if (idx >= 0) {
                selectedProduct = products[idx]
                binding.actvProduct.setText(products[idx].name, false)
            }
        }

        binding.btnConfirm.setOnClickListener { doTransfer() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun doTransfer() {
        val p = selectedProduct
        val from = fromWarehouse
        val to = toWarehouse
        if (p == null || from == null || to == null) {
            Toast.makeText(this, "همه فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
            return
        }
        if (from.id == to.id) {
            Toast.makeText(this, "انبار مبدأ و مقصد نباید یکی باشند", Toast.LENGTH_SHORT).show()
            return
        }
        val amount = binding.etAmount.text?.toString()?.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.etAmount.error = getString(R.string.error_invalid)
            return
        }
        val note = binding.etNote.text?.toString()?.trim().orEmpty()
        val ok = repository.transfer(p.id, from.id, to.id, amount, note = note)
        if (ok) {
            Toast.makeText(this, R.string.success_transfer, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, R.string.error_insufficient, Toast.LENGTH_SHORT).show()
        }
    }
}
