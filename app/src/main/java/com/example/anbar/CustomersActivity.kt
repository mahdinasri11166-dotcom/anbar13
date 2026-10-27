package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.anbar.databinding.ActivityCustomersBinding

class CustomersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomersBinding
    private lateinit var repository: InventoryRepository
    private lateinit var adapter: CustomerAdapter
    private var allCustomers: List<Customer> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCustomersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarCustomers)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.mod_customers)

        repository = InventoryRepository(this)
        adapter = CustomerAdapter(
            emptyList(),
            onClick = { c ->
                startActivity(Intent(this, CustomerDetailActivity::class.java).apply {
                    putExtra("customer_id", c.id)
                })
            },
            onLongClick = { c -> showCustomerActions(c) }
        )
        binding.recyclerCustomers.layoutManager = LinearLayoutManager(this)
        binding.recyclerCustomers.adapter = adapter

        binding.etSearchCustomer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString().orEmpty())
            }
        })

        binding.btnAddCustomer.setOnClickListener { showEditDialog(null) }
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
        allCustomers = repository.getCustomers()
        filter(binding.etSearchCustomer.text?.toString().orEmpty())
    }

    private fun filter(q: String) {
        val filtered = if (q.isBlank()) allCustomers
        else allCustomers.filter {
            it.name.contains(q, true) ||
                it.phone.contains(q) ||
                it.address.contains(q, true)
        }
        adapter.update(filtered)
        binding.tvEmptyCustomers.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerCustomers.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCustomerActions(c: Customer) {
        AlertDialog.Builder(this)
            .setTitle(c.name)
            .setItems(arrayOf("ویرایش", "حذف", "پروفایل")) { _, which ->
                when (which) {
                    0 -> showEditDialog(c)
                    1 -> confirmDelete(c)
                    2 -> startActivity(Intent(this, CustomerDetailActivity::class.java).apply {
                        putExtra("customer_id", c.id)
                    })
                }
            }
            .show()
    }

    private fun confirmDelete(c: Customer) {
        AlertDialog.Builder(this)
            .setTitle("حذف مشتری")
            .setMessage("«${c.name}» حذف شود؟\n(سوابق فروش باقی می‌ماند)")
            .setPositiveButton("حذف") { _, _ ->
                repository.deleteCustomer(c.id)
                Toast.makeText(this, "حذف شد", Toast.LENGTH_SHORT).show()
                load()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    /** null = افزودن جدید */
    private fun showEditDialog(existing: Customer?) {
        val etName = EditText(this).apply {
            hint = "نام مشتری *"
            setText(existing?.name.orEmpty())
            setPadding(40, 28, 40, 16)
        }
        val etPhone = EditText(this).apply {
            hint = getString(R.string.phone)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(existing?.phone.orEmpty())
            setPadding(40, 16, 40, 16)
        }
        val etAddress = EditText(this).apply {
            hint = "آدرس (اختیاری)"
            setText(existing?.address.orEmpty())
            minLines = 2
            setPadding(40, 16, 40, 16)
        }
        val etNotes = EditText(this).apply {
            hint = "یادداشت (اختیاری)"
            setText(existing?.notes.orEmpty())
            setPadding(40, 16, 40, 16)
        }
        val etCreditLimit = EditText(this).apply {
            hint = "سقف اعتبار (0=بدون سقف)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (existing != null && existing.creditLimit > 0) existing.creditLimit.toLong().toString() else "0")
            setPadding(40, 16, 40, 28)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(etName)
            addView(etPhone)
            addView(etAddress)
            addView(etNotes)
            addView(etCreditLimit)
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "افزودن مشتری" else "ویرایش مشتری")
            .setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.name_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val phone = etPhone.text.toString().trim()
                val address = etAddress.text.toString().trim()
                val notes = etNotes.text.toString().trim()
                val limit = etCreditLimit.text.toString().toDoubleOrNull() ?: 0.0
                if (existing == null) {
                    val added = repository.addCustomer(name, phone, address)
                    repository.updateCustomer(added.copy(notes = notes, creditLimit = limit))
                    Toast.makeText(this, "مشتری اضافه شد", Toast.LENGTH_SHORT).show()
                } else {
                    repository.updateCustomer(
                        existing.copy(
                            name = name,
                            phone = phone,
                            address = address,
                            notes = notes,
                            creditLimit = limit
                        )
                    )
                    Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
                }
                load()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
