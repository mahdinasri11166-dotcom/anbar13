package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anbar.databinding.ActivitySuppliersBinding

class SuppliersActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySuppliersBinding
    private lateinit var repository: InventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySuppliersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarSuppliers)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = InventoryRepository(this)
        binding.recyclerSuppliers.layoutManager = LinearLayoutManager(this)

        binding.btnAddSupplier.setOnClickListener { showAddDialog() }
        binding.btnNewPurchase.setOnClickListener {
            if (repository.getSuppliers().isEmpty()) {
                Toast.makeText(this, "ابتدا تامین‌کننده اضافه کنید", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, PurchaseActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load() {
        val list = repository.getSuppliers()
        binding.tvEmptySuppliers.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerSuppliers.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE

        binding.recyclerSuppliers.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_supplier, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val s = list[position]
                holder.itemView.findViewById<TextView>(R.id.tvSupName).text = s.name
                holder.itemView.findViewById<TextView>(R.id.tvSupPhone).text =
                    s.phone.ifBlank { "بدون شماره" }
                holder.itemView.findViewById<TextView>(R.id.tvSupPayable).text =
                    if (s.payableBalance > 0) format(s.payableBalance) else "۰"
                holder.itemView.setOnClickListener {
                    startActivity(Intent(this@SuppliersActivity, SupplierDetailActivity::class.java).apply {
                        putExtra("supplier_id", s.id)
                    })
                }
            }

            override fun getItemCount() = list.size
        }
    }

    private fun showAddDialog() {
        val etName = EditText(this).apply {
            hint = "نام تامین‌کننده *"
            setPadding(40, 30, 40, 20)
        }
        val etPhone = EditText(this).apply {
            hint = getString(R.string.phone)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(40, 20, 40, 20)
        }
        val etAddress = EditText(this).apply {
            hint = "آدرس (اختیاری)"
            setPadding(40, 20, 40, 30)
            minLines = 2
        }
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(etName)
            addView(etPhone)
            addView(etAddress)
        }
        AlertDialog.Builder(this)
            .setTitle("افزودن تامین‌کننده")
            .setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.name_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repository.addSupplier(
                    name,
                    etPhone.text.toString().trim(),
                    etAddress.text.toString().trim()
                )
                load()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun format(v: Double): String =
        if (v == v.toLong().toDouble()) String.format("%,d", v.toLong())
        else String.format("%,.0f", v)
}
