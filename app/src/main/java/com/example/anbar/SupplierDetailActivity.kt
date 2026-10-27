package com.example.anbar

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.anbar.databinding.ActivitySupplierDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SupplierDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupplierDetailBinding
    private lateinit var repository: InventoryRepository
    private var supplierId: Long = -1
    private var supplier: Supplier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupplierDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "پروفایل تامین‌کننده"

        repository = InventoryRepository(this)
        supplierId = intent.getLongExtra("supplier_id", -1L)
        binding.btnPaySupplier.setOnClickListener { showPayDialog() }
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
        supplier = repository.getSupplier(supplierId)
        val s = supplier ?: run {
            finish()
            return
        }
        binding.tvSupDetailName.text = s.name
        binding.tvSupDetailPhone.text = s.phone.ifBlank { "بدون شماره" }
        binding.tvSupDetailPayable.text = "بدهی ما: ${format(s.payableBalance)}"

        val purchases = repository.getSupplierPurchases(s.id)
        binding.layoutPurchaseHistory.removeAllViews()
        if (purchases.isEmpty()) {
            binding.tvNoPurchases.visibility = View.VISIBLE
        } else {
            binding.tvNoPurchases.visibility = View.GONE
            val df = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
            purchases.forEach { p ->
                val tv = TextView(this).apply {
                    text = "${p.invoiceNumber}  ·  ${df.format(Date(p.timestamp))}\n" +
                            "مبلغ: ${format(p.subtotal)}" +
                            if (p.isCredit) "  (نسیه)" else "  (نقدی)"
                    textSize = 13f
                    setPadding(12, 10, 12, 10)
                    setBackgroundColor(0xFFFFFFFF.toInt())
                }
                val card = com.google.android.material.card.MaterialCardView(this).apply {
                    radius = 12f
                    cardElevation = 2f
                    useCompatPadding = true
                    addView(tv)
                }
                binding.layoutPurchaseHistory.addView(card)
            }
        }
    }

    private fun showPayDialog() {
        val s = supplier ?: return
        if (s.payableBalance <= 0) {
            Toast.makeText(this, "بدهی ندارد", Toast.LENGTH_SHORT).show()
            return
        }
        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "مبلغ پرداختی"
            setText(s.payableBalance.toLong().toString())
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("پرداخت به ${s.name}")
            .setView(et)
            .setPositiveButton("ثبت") { _, _ ->
                val amount = et.text.toString().toDoubleOrNull() ?: 0.0
                if (amount <= 0) return@setPositiveButton
                repository.paySupplier(s.id, amount)
                Toast.makeText(this, "پرداخت ثبت شد", Toast.LENGTH_SHORT).show()
                load()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun format(v: Double): String =
        if (v == v.toLong().toDouble()) String.format("%,d", v.toLong())
        else String.format("%,.0f", v)
}
