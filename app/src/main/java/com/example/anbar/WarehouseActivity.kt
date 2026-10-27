package com.example.anbar

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anbar.databinding.ActivityWarehouseBinding

class WarehouseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWarehouseBinding
    private lateinit var repository: InventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWarehouseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.warehouses)

        repository = InventoryRepository(this)
        loadList()

        binding.btnAddWarehouse.setOnClickListener {
            val name = binding.etNewWarehouse.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                binding.etNewWarehouse.error = "نام الزامی است"
                return@setOnClickListener
            }
            repository.addWarehouse(name)
            binding.etNewWarehouse.text?.clear()
            loadList()
            Toast.makeText(this, "انبار اضافه شد", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadList() {
        val list = repository.getWarehouses()
        binding.recyclerWarehouses.layoutManager = LinearLayoutManager(this)
        binding.recyclerWarehouses.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = android.widget.TextView(parent.context).apply {
                    setPadding(32, 28, 32, 28)
                    textSize = 16f
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val w = list[position]
                (holder.itemView as android.widget.TextView).text =
                    "${w.name}" + if (w.isDefault) "  (پیش‌فرض)" else ""
            }

            override fun getItemCount() = list.size
        }
    }
}
